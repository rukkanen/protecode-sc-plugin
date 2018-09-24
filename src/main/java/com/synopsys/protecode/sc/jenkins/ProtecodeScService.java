 /*******************************************************************************
  * Copyright (c) 2017 Synopsys, Inc
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    Synopsys, Inc - initial implementation and documentation
  *******************************************************************************/
package com.synopsys.protecode.sc.jenkins;

import com.synopsys.protecode.sc.jenkins.interfaces.Listeners.ErrorService;
import com.synopsys.protecode.sc.jenkins.interfaces.Listeners.GroupService;
import com.synopsys.protecode.sc.jenkins.interfaces.Listeners.PollService;
import com.synopsys.protecode.sc.jenkins.interfaces.Listeners.ResultService;
import com.synopsys.protecode.sc.jenkins.interfaces.Listeners.ScanService;
import com.synopsys.protecode.sc.jenkins.interfaces.ProtecodeScApi;
import com.synopsys.protecode.sc.jenkins.interfaces.ProtecodeScServicesApi;
import com.synopsys.protecode.sc.jenkins.types.ConnectionStatus;
import com.synopsys.protecode.sc.jenkins.types.HttpTypes;
import com.synopsys.protecode.sc.jenkins.utils.UtilitiesGeneral;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;
import lombok.Data;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This class implements and encapsulates the interface towards Protecode.
 *
 * As a service class it won't know what it's calling, so the backend must be provided
 * for it.
 */
public @Data class ProtecodeScService {
  
  private static final Logger LOGGER = Logger.getLogger(ProtecodeScService.class.getName());
  private ProtecodeScApi backend = null;
  private ProtecodeScServicesApi serviceBackend = null;
  
  public ProtecodeScService(String credentialsId, URL host, boolean checkCertificate){
    backend = ProtecodeScConnection.backend(credentialsId, host, checkCertificate);
    serviceBackend = ProtecodeScConnection.serviceBackend(host, checkCertificate);
  }
  
  public void scan(String group, String fileName, RequestBody requestBody, ScanService listener) {   
    Call<HttpTypes.UploadResponse> call = backend.scan(
      group,
      UtilitiesGeneral.replaceSpaceWithUnderscore(fileName),
      requestBody
    );
    call.enqueue(new Callback<HttpTypes.UploadResponse>() {
      @Override
      public void onResponse(
        Call<HttpTypes.UploadResponse> call,
        Response<HttpTypes.UploadResponse> response
      ) {
        if (response.isSuccessful()) {
          listener.processUploadResult(response.body());
        } else {
          try {
            listener.setError("Protecode SC returned error for " +
              response.errorBody().string() + " for file: " + fileName);
          } catch (IOException ex) {
            listener.setError("Protecode SC returned generic error without error message"
              + " for file: " + fileName);
          }
        }
      }
      @Override
      public void onFailure(Call<HttpTypes.UploadResponse> call, Throwable t) {
        // something went completely south (like no internet connection)
        String error = "Protecode SC returned error for file scan request: " + fileName +
          ": " + t.getLocalizedMessage();  
        fail(error, listener);
      }
    });
  }
  
  // TODO: This is a copy paste
  public void scanFetchFromUrl(
          String group, 
          String url, 
          Map headers, 
          ScanService listener
  ) {
    headers.put("Group", group);
    headers.put("Url", url);
    
    Call<HttpTypes.UploadResponse> call = backend.scanFetchFromUrl(headers);
    call.enqueue(new Callback<HttpTypes.UploadResponse>() {
      @Override
      public void onResponse(
        Call<HttpTypes.UploadResponse> call,
        Response<HttpTypes.UploadResponse> response
      ) {
        if (response.isSuccessful()) {
          listener.processUploadResult(response.body());
        } else {
          try {            
            listener.setError("Protecode SC returned error for " +
              response.errorBody().string() + " for url: " + url);            
          } catch (IOException ex) {
            listener.setError("Protecode SC returned generic error without error message"
              + " for url: " + url);
          }
        }
      }
      @Override
      public void onFailure(Call<HttpTypes.UploadResponse> call, Throwable t) {
        // something went completely south (like no internet connection)
        String error = "Protecode SC returned error for url scan request: " + url +
          ": " + t.getLocalizedMessage();  
        fail(error, listener);
      }
    });
  }
  
  public void poll(Integer scanId, PollService listener) {
    Call<HttpTypes.UploadResponse> call = backend.poll(scanId);
    call.enqueue(new Callback<HttpTypes.UploadResponse>() {
      @Override
      public void onResponse(Call<HttpTypes.UploadResponse> call,
        Response<HttpTypes.UploadResponse> response) {
        if (response.isSuccessful()) {
          listener.setScanStatus(response.body());
        } else {
          try {
            listener.setError("Protecode SC returned error for " +
              response.errorBody().string() + " for scan id: " + scanId);
          } catch (IOException ex) {
            listener.setError("Protecode SC returned generic error without error message "
              + "for scan id: " + scanId);
          }
        }
      }
      @Override
      public void onFailure(Call<HttpTypes.UploadResponse> call, Throwable t) {
        String error = "Poll request returned with error for scan id: " + scanId + ". Error was: " + t.getLocalizedMessage();
        fail(error, listener);
      }
    });
  }
  
  public void scanResult(String sha1sum, ResultService listener) {
    Call<HttpTypes.ScanResultResponse> call = backend.scanResult(sha1sum);
    call.enqueue(new Callback<HttpTypes.ScanResultResponse>() {
      @Override
      public void onResponse(Call<HttpTypes.ScanResultResponse> call, Response<HttpTypes.ScanResultResponse> response) {
        if (response.isSuccessful()) {
          listener.setScanResult(response.body());
        } else {
          String error = "Fetching the scan result for sha1sum: " + sha1sum + " failed.";
          fail(error, listener);
        }
      }
      @Override
      public void onFailure(Call<HttpTypes.ScanResultResponse> call, Throwable t) {
        String error = "Fetching the scan result for sha1sum: " + sha1sum + " failed with error: " + t.getMessage();
        fail(error, listener);
      }
    });
  }
  
  /**
   * Test the connection with a HEAD call.
   * @return ConnectionStatus object for the current connection.
   */
  public ConnectionStatus connectionOk() {
    // TODO: Fix, this doesn't seem to work
    Call<Void> call = serviceBackend.head();
    try {
      return new ConnectionStatus(call.execute());
    } catch (IOException ex) {
      return new ConnectionStatus(ex);
    }
  }
  
  /**
   * Fetch groups for a user
   * @param listener GroupService instance to handle responses
   */
  public void groups(GroupService listener) {
    Call<HttpTypes.Groups> call = backend.groups();
    call.enqueue(new Callback<HttpTypes.Groups>() {
      @Override
      public void onResponse(Call<HttpTypes.Groups> call, Response<HttpTypes.Groups> response) {
        if (response.isSuccessful()) {
          listener.setGroups(response.body());
        } else {          
          String error = "Fetching groups failed";
          fail(error, listener);
        }
      }
      
      @Override
      public void onFailure(Call<HttpTypes.Groups> call, Throwable t) {
        String error = "Fetching groups failed with error: " + t.getMessage();
        fail(error, listener);
      }
    });
  }
  
  private void fail(String error, ErrorService listener) {
    listener.setError(error);
    LOGGER.warning(error);
  }
}
