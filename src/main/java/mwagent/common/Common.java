package mwagent.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import mwagent.vo.CommandVO;
import mwagent.vo.MwResponseVO;
import mwagent.vo.ResultVO;

public class Common {

	private static CloseableHttpClient httpClient = null;
	private static CloseableHttpClient httpsClient = null;
	private static CloseableHttpClient mtlsHttpClient = null;
	private static Config config = Config.getConfig();

	/**
	 * Mask token for secure logging - shows only last 10 characters
	 */
	private static String maskToken(String token) {
		if (token == null || token.length() <= 10) {
			return "**********";
		}
		return token.substring(token.length() - 10);
	}
	
	public static ArrayList<ResultVO> makeOneResultArray(ResultVO rv, CommandVO command){
		ArrayList<ResultVO> rvs = new ArrayList<ResultVO>();
		rvs.add(fillResult(rv, command));
		return rvs;
	}

	public static ResultVO fillResult(ResultVO rv, CommandVO command){
		rv.setHostName(command.getHostName());
		rv.setTargetFileName(command.getTargetFileName());
		rv.setTargetFilePath(command.getTargetFilePath());
		return rv;
	}

	private static CloseableHttpClient getHttpClient(String url){
		
		if (url.toLowerCase().startsWith("https")) {
			if(httpsClient==null)createHttpsClient();
			return httpsClient;
		}else{
			if(httpClient==null)httpClient = HttpClients.createDefault();
			return httpClient;
		}		    	
	}
	
    public static void createHttpsClient() {
    	
    	// 0. TLSv1.2 용 Security Provider 선택
    	if(config.getOs().equalsIgnoreCase("AIX")){
    		Security.addProvider(new BouncyCastleProvider());
    	}
    		
	    // 1~2. SSL socket factory 생성
	    //   ssl_verify=false (기본) : 모든 인증서 신뢰, 호스트네임 검증 안 함
	    //   ssl_verify=true         : truststore.path (없으면 JVM cacerts) 로 검증 + 호스트네임 검증
	    SSLConnectionSocketFactory sslScoketFactory;
		try {
			sslScoketFactory = TlsSupport.httpsSocketFactory(
					config.isSslVerify(), config.getTruststorePath(), config.getTruststorePassword());
			config.getLogger().info(TlsSupport.describe(config.isSslVerify(), config.getTruststorePath()));
		} catch (Exception e) {
			// 검증 모드에서 truststore 를 못 읽으면 trust-all 로 물러서지 않는다
			config.getLogger().log(Level.SEVERE, "Failed to create HTTPS client: " + e.getMessage(), e);
			throw new IllegalStateException("HTTPS client creation failed", e);
		}
	    	
	    // 3. CloseableHttpClient 생성: 커스텀 SSL socket factory 사용
	    httpsClient = HttpClients.custom()
	                .setSSLSocketFactory(sslScoketFactory)
	                .build();
	    
	    // 4. http
	    if(httpClient==null)httpClient = HttpClients.createDefault();

    }

    public static void createMtlsClient() {

    	if (!config.isUseMtls()) {
    		config.getLogger().info("mTLS is disabled, skipping mTLS client creation");
    		return;
    	}

    	config.getLogger().info("Creating mTLS client with client certificate...");

    	try {
    		// 1. Load client keystore (contains client certificate and private key)
    		KeyStore keyStore = KeyStore.getInstance("PKCS12");
    		FileInputStream keystoreStream = new FileInputStream(config.getClientKeystorePath());
    		keyStore.load(keystoreStream, config.getClientKeystorePassword().toCharArray());
    		keystoreStream.close();

    		// 2. Load truststore (contains server CA certificate)
    		KeyStore trustStore = TlsSupport.loadTruststore(config.getTruststorePath(), config.getTruststorePassword());

    		// 3. Create SSLContext with client key material (mTLS)
    		SSLContext sslContext = SSLContexts.custom()
    				.setProtocol("TLSv1.2")
    				.loadKeyMaterial(keyStore, config.getClientKeystorePassword().toCharArray())
    				.loadTrustMaterial(trustStore, null)
    				.build();

    		// 4. Create mTLS HttpClient
    		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
    				sslContext,
    				TlsSupport.hostnameVerifier(config.isSslVerify())
    		);

    		mtlsHttpClient = HttpClients.custom()
    				.setSSLSocketFactory(sslSocketFactory)
    				.build();

    		config.getLogger().info("mTLS client created successfully");

    	} catch (Exception e) {
    		config.getLogger().log(Level.SEVERE, "Failed to create mTLS client", e);
    		throw new RuntimeException("mTLS client creation failed", e);
    	}
    }

    public static MwResponseVO httpPOST(String path, String token, String data) {
    	
    	MwResponseVO mrvo = new MwResponseVO();
    	
    	String url = config.getServer_url() + path;

        try {
			
        	HttpClient httpClient = getHttpClient(url);

			HttpPost request = new HttpPost(url);
			request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer "+token);
	        request.setEntity(new StringEntity(data, "UTF-8"));

			HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            mrvo.setStatusCode(response.getStatusLine().getStatusCode());
            
            if (mrvo.getStatusCode() == 401) {
                config.getLogger().warning("Authentication failed (401) for URL: " + url);
            }

            if (entity != null) {
            	
            	String value = EntityUtils.toString(entity);
    			
                JSONParser jsonPar = new JSONParser();
                JSONObject jsonObj = null;
                
                try {
                	jsonObj = (JSONObject) jsonPar.parse(value);
                    config.getLogger().fine("HTTP POST response from " + url + " (" + value.length() + " chars)");
                }catch(ParseException e){
                	config.getLogger().warning("JSON Parsing Error  data : "+LogSafe.safe(value));
                } 
                
                mrvo.setResponse(jsonObj);
                
            }
        	
        } catch(IOException e){
        	config.getLogger().warning("HTTP execution failed" + " : " + url);
        	mrvo.setStatusCode(-104);
        }catch(Exception e){
        	config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-105);
        }
        
        return mrvo;

    }

    /**
     * HTTP POST with application/x-www-form-urlencoded content type (for OAuth2 token requests)
     */
    public static MwResponseVO httpPOSTFormUrlEncoded(String path, String data) {

    	MwResponseVO mrvo = new MwResponseVO();

    	String url = config.getServer_url() + path;

        try {

        	HttpClient httpClient = getHttpClient(url);

			HttpPost request = new HttpPost(url);
			request.addHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
	        request.setEntity(new StringEntity(data, "UTF-8"));

			HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            mrvo.setStatusCode(response.getStatusLine().getStatusCode());

            if (mrvo.getStatusCode() == 401) {
                config.getLogger().warning("Authentication failed (401) for URL: " + url);
            }

            if (entity != null) {

            	String value = EntityUtils.toString(entity);

                JSONParser jsonPar = new JSONParser();
                JSONObject jsonObj = null;

                try {
                	jsonObj = (JSONObject) jsonPar.parse(value);
                    config.getLogger().fine("HTTP POST Form response from " + url + " (" + value.length() + " chars)");
                }catch(ParseException e){
                	config.getLogger().warning("JSON Parsing Error  data : "+LogSafe.safe(value));
                }

                mrvo.setResponse(jsonObj);

            }

        } catch(IOException e){
        	config.getLogger().warning("HTTP execution failed" + " : " + url);
        	mrvo.setStatusCode(-104);
        }catch(Exception e){
        	config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-105);
        }

        return mrvo;

    }

    public static MwResponseVO httpGET(String path, String token) {
    	return httpGET(path, token, null);
    }

    /**
     * extraHeaders 는 추가로 실을 요청 헤더. null 이거나 값이 null 인 항목은 보내지 않는다.
     */
    public static MwResponseVO httpGET(String path, String token, Map<String, String> extraHeaders) {
    	
    	MwResponseVO mrvo = new MwResponseVO();
    	
    	String url = config.getServer_url() + path;
    	
        try {
			
        	HttpClient httpClient = getHttpClient(url);
            
        	HttpGet request = new HttpGet(url);
            request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer "+token);
			if (extraHeaders != null) {
				for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
					if (h.getValue() != null) {
						request.addHeader(h.getKey(), h.getValue());
					}
				}
			}

			HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            mrvo.setStatusCode(response.getStatusLine().getStatusCode());
            
            if (mrvo.getStatusCode() == 401) {
                config.getLogger().warning("Authentication failed (401) for URL: " + url);
            }

            if (entity != null) {
            	
            	String value = EntityUtils.toString(entity);
    			
                JSONParser jsonPar = new JSONParser();
                JSONObject jsonObj = null;
                
                try {
                	jsonObj = (JSONObject) jsonPar.parse(value);
                    config.getLogger().fine("HTTP GET response from " + url + " (" + value.length() + " chars)");
                }catch(ParseException e){
                	config.getLogger().warning("JSON Parsing Error  data : "+LogSafe.safe(value));
                } 
                
                mrvo.setResponse(jsonObj);
                
            }
        	
        } catch(IOException e){
        	config.getLogger().warning("HTTP execution failed" + " : " + url);
        	mrvo.setStatusCode(-110);
        }catch(Exception e){
        	config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-111);
        }
        
        return mrvo;
        
    }

    public static MwResponseVO httpFileDownload(String uri, String token, String file_location) {
    	
    	MwResponseVO mrvo = new MwResponseVO();
    	
    	String fullname = "";
    	
        try {

        	HttpClient httpClient = getHttpClient(uri);

        	HttpGet request = new HttpGet(uri);
            request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			if (token != null && !token.isEmpty()) {
				request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer "+token);
			}

			HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            int returnCode = response.getStatusLine().getStatusCode();
            
            mrvo.setStatusCode(returnCode);
            config.getLogger().fine("File Download status code  " + returnCode);	
            
            if (returnCode >= 200 && returnCode < 300 && entity != null) {            	
            	
				String filename = "downloaded_file";
				org.apache.http.Header contentDisposition = response.getFirstHeader("Content-Disposition");
				if (contentDisposition != null) {
					String name = contentDisposition.getValue();
					filename = name.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
					config.getLogger().info("Filename from Content-Disposition: " + filename);
				} else {
					// Extract from URL if possible
					try {
						String path = new java.net.URI(uri).getPath();
						if (path != null && path.contains("/")) {
							String lastPart = path.substring(path.lastIndexOf('/') + 1);
							if (!lastPart.isEmpty()) {
								filename = lastPart;
							}
						}
					} catch (Exception e) {
						// Fallback to default
					}
					config.getLogger().info("Filename from URL/Fallback: " + filename);
				}

				// The name comes from the server response (header or URL); never let it leave file_location
				if (!SecurityValidator.isValidFilename(filename)) {
					config.getLogger().severe("Security: Invalid download file name: " + filename);
					EntityUtils.consumeQuietly(entity);
					mrvo.setStatusCode(-103);
					return mrvo;
				}

				fullname = file_location + filename;
				FileOutputStream fos = new FileOutputStream(fullname);
				entity.writeTo(fos);
				fos.close();
                
				mrvo.setFileName(filename);
				mrvo.setFileLocation(file_location);
                
            }
            
		}catch(FileNotFoundException e){
			config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-101);
        } catch(IOException e){
        	config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-100);
        } catch(Exception e){
        	config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	mrvo.setStatusCode(-102);
        }
        
        return mrvo;
        
    }

	public static long updateRefreshToken(){

        int rtn = 0;
        
		String path = "/api/v1/agent/getRefreshToken/" + config.getAgent_id();
			
		config.getLogger().fine("updateRefreshToken path : "+path);

		MwResponseVO mrvo = Common.httpGET(path, config.getAccess_token());

        if(mrvo.getStatusCode()!=200){
        	
        	config.getLogger().severe("getRefreshToken response error : "+Integer.toString(mrvo.getStatusCode()));
            rtn = -1;
            
        }else if(mrvo.getResponse() != null) {
            	
            String refresh_token = (String)mrvo.getResponse().get("refresh_token");
            config.getLogger().fine("refresh_token :***" + maskToken(refresh_token));
            config.setRefresh_token(refresh_token);
            rtn = 1;
            
        }else{
        	
        	rtn = -2;
        	
        }
        
        return rtn;
    	
    }

	public static int applyRefreshToken(){

		long rtn = Common.updateRefreshToken();
	
		if(rtn<0){
			config.getLogger().severe("Failed when getting a Refresh Token.");
			return -1;
		}

		rtn = config.updatePropertyLegacy("token", config.getRefresh_token());

		if(rtn<0){
			config.getLogger().severe("Failed when updating Refresh Token propery.");
			return -2;
		}
		
		return 1;
		
	}
	
	public static int updateToken() {

        int rtn = 0;

        // mTLS enabled: use OAuth2 Token Endpoint
        // mTLS disabled: use legacy endpoint
        if (config.isUseMtls()) {
            // OAuth2 Token Endpoint (for mTLS)
            String path = "/oauth2/token";
            config.getLogger().info("OAuth2 refresh_token grant: " + path);

            // OAuth2 standard: application/x-www-form-urlencoded
            String body = "grant_type=refresh_token&refresh_token=" + config.getRefresh_token();

            MwResponseVO mrvo = Common.httpPOSTFormUrlEncoded(path, body);

            config.getLogger().fine("OAuth2 token response code: " + Integer.toString(mrvo.getStatusCode()));

            if (mrvo.getStatusCode() == 200 && mrvo.getResponse() != null) {

                String access_token = (String)mrvo.getResponse().get("access_token");
                String token_type = (String)mrvo.getResponse().get("token_type");
                Long expires_in = (Long)mrvo.getResponse().get("expires_in");
                String scope = (String)mrvo.getResponse().get("scope");

                config.setAccess_token(access_token);
                config.getLogger().info("OAuth2 access_token received (type=" + token_type + ", expires_in=" + expires_in + ", scope=" + scope + ")");
                rtn = 1;

            } else if (mrvo.getStatusCode() == 401) {
                // Refresh token expired or invalid
                config.getLogger().warning("OAuth2 refresh_token expired or invalid (401)");
                rtn = -401;

            } else {
                config.getLogger().warning("OAuth2 token response error: " + mrvo.getStatusCode());
                rtn = -1;
            }
        } else {
            // Legacy endpoint (for non-mTLS)
            String path = "/api/v1/security/refresh";
            config.getLogger().info("Legacy token refresh: " + path);

            MwResponseVO mrvo = Common.httpPOST(path, config.getRefresh_token(), "");

            config.getLogger().fine("Legacy token response code: " + Integer.toString(mrvo.getStatusCode()));

            if (mrvo.getStatusCode() == 200 && mrvo.getResponse() != null) {

                String access_token = (String)mrvo.getResponse().get("access_token");
                config.setAccess_token(access_token);
                config.getLogger().info("Legacy access_token received");
                rtn = 1;

            } else if (mrvo.getStatusCode() == 401) {
                config.getLogger().warning("Legacy token expired or invalid (401)");
                rtn = -401;

            } else {
                config.getLogger().warning("Legacy token response error: " + mrvo.getStatusCode());
                rtn = -1;
            }
        }

        return rtn;
    }

	/**
	 * Cascading token renewal strategy:
	 * 1. Try refresh_token grant first
	 * 2. If refresh_token expired (401), fallback to mTLS client_credentials grant
	 *
	 * @return 1 on success, negative value on failure
	 */
	public static int renewAccessTokenWithFallback() {

		config.getLogger().info("=== Cascading Token Renewal ===");

		// Step 1: Try refresh_token grant
		config.getLogger().info("Step 1: Attempting refresh_token grant...");
		int result = updateToken();

		if (result == 1) {
			config.getLogger().info("Token renewed successfully via refresh_token");
			return 1;
		}

		// Step 2: If refresh_token expired, try mTLS
		if (result == -401 && config.isUseMtls()) {
			config.getLogger().info("Step 2: refresh_token expired, attempting mTLS client_credentials grant...");

			result = renewAccessTokenWithMtls();

			if (result == 1) {
				config.getLogger().info("Token renewed successfully via mTLS client_credentials");
				return 1;
			} else {
				config.getLogger().severe("mTLS token renewal failed with code: " + result);
				return result;
			}
		}

		// mTLS not enabled or other error
		if (result == -401 && !config.isUseMtls()) {
			config.getLogger().severe("refresh_token expired and mTLS is not enabled - cannot renew token");
			return -401;
		}

		config.getLogger().severe("Token renewal failed with code: " + result);
		return result;
	}

	public static int renewAccessTokenWithMtls() {

		if (!config.isUseMtls()) {
			config.getLogger().warning("mTLS is not enabled, cannot renew token with mTLS");
			return -1;
		}

		if (mtlsHttpClient == null) {
			config.getLogger().warning("mTLS client not initialized, creating now...");
			try {
				createMtlsClient();
			} catch (Exception e) {
				config.getLogger().log(Level.SEVERE, "Failed to create mTLS client", e);
				return -2;
			}
		}

		int rtn = 0;

		// OAuth2 Token Endpoint with client_credentials grant
		String path = "/oauth2/token";
		String url = config.getServer_url() + path;

		config.getLogger().info("OAuth2 client_credentials grant (mTLS): " + url);

		try {
			HttpPost request = new HttpPost(url);

			// OAuth2 standard: application/x-www-form-urlencoded
			request.addHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

			// OAuth2 client_credentials grant
			String body = "grant_type=client_credentials";
			request.setEntity(new StringEntity(body));

			HttpResponse response = mtlsHttpClient.execute(request);
			HttpEntity entity = response.getEntity();

			int statusCode = response.getStatusLine().getStatusCode();
			config.getLogger().fine("OAuth2 client_credentials response code: " + statusCode);

			if (statusCode == 200 && entity != null) {
				String value = EntityUtils.toString(entity);

				JSONParser jsonPar = new JSONParser();
				JSONObject jsonObj = null;

				try {
					jsonObj = (JSONObject) jsonPar.parse(value);
				} catch (ParseException e) {
					config.getLogger().warning("JSON Parsing Error data: " + LogSafe.safe(value));
					return -3;
				}

				if (jsonObj != null) {
					String access_token = (String) jsonObj.get("access_token");
					String token_type = (String) jsonObj.get("token_type");
					Long expires_in = (Long) jsonObj.get("expires_in");
					String scope = (String) jsonObj.get("scope");

					config.setAccess_token(access_token);
					config.getLogger().info("OAuth2 access_token received via mTLS (type=" + token_type +
							", expires_in=" + expires_in + ", scope=" + scope + ")");
					rtn = 1;
				} else {
					rtn = -4;
				}

			} else {
				config.getLogger().severe("OAuth2 token request failed with status: " + statusCode);
				rtn = -5;
			}

		} catch (IOException e) {
			config.getLogger().log(Level.WARNING, "HTTP execution failed: " + url, e);
			rtn = -6;
		} catch (Exception e) {
			config.getLogger().log(Level.WARNING, "OAuth2 mTLS token request failed", e);
			rtn = -7;
		}

		return rtn;
	}

    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }

        String escaped = raw;
        escaped = escaped.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\b", "\\b");
        escaped = escaped.replace("\f", "\\f");
        escaped = escaped.replace("\n", "\\n");
        escaped = escaped.replace("\r", "\\r");
        escaped = escaped.replace("\t", "\\t");

        return escaped;
    }

}
