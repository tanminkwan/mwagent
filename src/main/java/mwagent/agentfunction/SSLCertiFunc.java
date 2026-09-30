package mwagent.agentfunction;

import java.net.InetSocketAddress;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import mwagent.common.Common;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;
import static mwagent.common.Config.getConfig;

/**
 * Checks and retrieves SSL certificate information for a domain.
 * 
 * Supported additional_params (String):
 * - Domain name or domain:port. Defaults to port 443.
 * - JSON string: {"domain_name": "...", "port": ..., "ip": "..."}
 * 
 * Example additional_params:
 * "google.com" or "example.com:8443" or "{\"domain_name\":\"google.com\",\"port\":443,\"ip\":\"142.250.190.46\"}"
 */
public class SSLCertiFunc implements AgentFunc {

	@Override
	public ArrayList<ResultVO> exeCommand(CommandVO command) throws Exception {

		String param = command.getAdditionalParams();
		
		String domain = "";
		String port = "443";
		String ip = "127.0.0.1";

		if (param != null && param.trim().startsWith("{")) {
			try {
				JSONParser parser = new JSONParser();
				JSONObject json = (JSONObject) parser.parse(param);
				if (json.get("domain_name") != null) domain = (String) json.get("domain_name");
				if (json.get("port") != null) port = String.valueOf(json.get("port"));
				if (json.get("ip") != null) ip = (String) json.get("ip");
				
				if (domain.isEmpty()) {
					String[] dnp = getDomainNPort(param);
					domain = dnp[0];
					port = dnp[1];
				}
			} catch (Exception e) {
				String[] dnp = getDomainNPort(param);
				domain = dnp[0];
				port = dnp[1];
			}
		} else {
			String[] dnp = getDomainNPort(param);
			domain = dnp[0];
			port = dnp[1];
		}
				
		X509Certificate[] validCerts = checkSSLCertificate(domain, ip, Integer.parseInt(port));
		
		ResultVO rv = printValidCerts(domain, validCerts);
		
		return Common.makeOneResultArray(rv, command);
	
	}
	
	@SuppressWarnings("unchecked")	
	public ResultVO printValidCerts(String domain, X509Certificate[] certs) {
		

        /*
    	StringBuilder json = new StringBuilder();
        json.append("{\"domain\":\"" + domain + "\",\"certs\":[");

        int i = 0;
		for(X509Certificate c:certs){
			i++;
			json.append("{");
			json.append("\"index\":\""+Integer.toString(i)+"\",");
			json.append("\"notafter\":\""+c.getNotAfter().toString()+"\",");
			json.append("\"notbefore\":\""+c.getNotBefore().toString()+"\",");
			json.append("\"serial\":\""+String.format("%032X", c.getSerialNumber())+"\",");
			json.append("\"issuer\":\""+c.getIssuerDN().getName()+"\",");
            json.append("\"subject\":\""+c.getSubjectDN().getName()+"\"");
            json.append("}");
            if(i!=certs.length){
            	json.append(",");
            }
		}
    
		json.append("]}");
		*/
        
		JSONObject resultJson = new JSONObject();
		resultJson.put("domain", domain);
		
		JSONArray certsArray = new JSONArray();
		
		int i = 0;
		for(X509Certificate c:certs){
			
			i++;
			JSONObject certObj = new JSONObject();
			certObj.put("index", Integer.toString(i));
			certObj.put("isCA", c.getBasicConstraints() != -1);
			certObj.put("notafter", c.getNotAfter().toString());
			certObj.put("notbefore", c.getNotBefore().toString());
			certObj.put("serial", String.format("%032X", c.getSerialNumber()));
			certObj.put("issuer", c.getIssuerDN().getName());
			certObj.put("subject", c.getSubjectDN().getName());
			
			certsArray.add(certObj);
			
		}
		
		resultJson.put("certs", certsArray);
		
		ResultVO rv = new ResultVO();

		rv.setResult(resultJson.toJSONString());
		rv.setOk(true);

        return rv;
        
	}

	public String[] getDomainNPort(String param) {
		
		String domain;
		String port;
		
		if(param == null){
			param = "";
		}
		param = param.trim();

		if(param.contains(":")){
			String[] parts = param.split(":");

			domain = parts[0];
			port = parts[1];
		}
		else{
			domain = param;
			port = "443";
		}

		return new String[]{domain, port};
	}
	
	// AIX java1.8 u144 의 기본 security 가 TLSv1.2 를 지원하지 않아서 외부 Security Provider 를 추가함
	private void addSecurityProvider(){
		
		if(getConfig().getOs().equals("AIX")){
			
			try{
				
				Class<?> bcClazz = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
				Provider bcProvider = (Provider) bcClazz.getDeclaredConstructor().newInstance();
				Security.addProvider(bcProvider);
				
			}catch(ClassNotFoundException e){
				getConfig().getLogger().log(Level.SEVERE, "ClassNotFoundException : BouncyCastleProvider", e);
			}catch(Exception e){
					getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
			}
			
		}
		
	}
	
	public X509Certificate[] checkSSLCertificate(String domain, String ip, int port) {
		
		List<X509Certificate> validCerts = new ArrayList<>();
		
		try{
			
			addSecurityProvider();
			
			// added 2025.11.07
			TrustManager[] trustAllCerts = new TrustManager[] {
					new X509TrustManager(){
						public X509Certificate[] getAcceptedIssuers(){
							return null;
						}
						public void checkClientTrusted(X509Certificate[] certs, String authType){}
						public void checkServerTrusted(X509Certificate[] certs, String authType){}
					}
			};

			SSLContext context = SSLContext.getInstance("TLSv1.2");
			// commented 2025.11.07 
			//context.init(null, null, null);
			
			// added 2025.11.07
			context.init(null, trustAllCerts, new java.security.SecureRandom());

			SSLSocketFactory factory = context.getSocketFactory();
			
			try(SSLSocket socket = (SSLSocket) factory.createSocket()){
				
				socket.connect(new InetSocketAddress(ip, port));
				SSLParameters sslParams = socket.getSSLParameters();
				
				// 1) SNIHostName(domain) 설정
				sslParams.setServerNames(
					java.util.Collections.singletonList(
						new javax.net.ssl.SNIHostName(domain)
					)
				);
				//sslParams.setEndpointIdentificationAlgorithm("HTTPS");
				socket.setSSLParameters(sslParams);
				socket.startHandshake();
				
				SSLSession session = socket.getSession();
				Certificate[] certificates = session.getPeerCertificates();
				
				if (certificates != null && certificates.length > 0) {
					Certificate firstCert = certificates[0];
					if (firstCert instanceof X509Certificate) {
						X509Certificate leafCert = (X509Certificate) firstCert;
						
						// Leaf 인증서가 도메인과 일치하는지 확인 후, 일치하면 체인 전체를 추가
						if (isCertificateValidForDomain(leafCert, domain)) {
							for (Certificate certificate : certificates) {
								if (certificate instanceof X509Certificate) {
									validCerts.add((X509Certificate) certificate);
								}
							}
						}
					}
				}
			}
			
		} catch (Exception e){
			getConfig().getLogger().log(Level.SEVERE, "Failed to check SSL certificate: " + e.getMessage(), e);
			return new X509Certificate[0];
		}
		
		return validCerts.toArray(new X509Certificate[0]);
		
	}
	
	private boolean isCertificateValidForDomain(X509Certificate cert, String domain) {
    	
    	if(domain.equals("*"))return true;
    	
        try {
            // Get subject alternative names (SANs) from the certificate
            Collection<List<?>> sanEntries = cert.getSubjectAlternativeNames();
            if (sanEntries != null) {
                for (List<?> sanEntry : sanEntries) {
                    if (sanEntry != null && sanEntry.size() >= 2) {
                        Object sanValue = sanEntry.get(1);
                        if (sanValue instanceof String && matchesDomain((String) sanValue, domain)) {
                            return true;
                        }
                    }
                }
            }

            // Check the common name (CN) in the subject if SANs are not present
            String subjectDN = cert.getSubjectX500Principal().getName();
            String cn = getCommonName(subjectDN);
			return matchesDomain(cn, domain);
        } catch (Exception e) {
			getConfig().getLogger().log(Level.SEVERE, "isCertificateValidForDomain: " + e.getMessage(), e);
        }
        return false;
    }

    private String getCommonName(String subjectDN) {
        String[] dnComponents = subjectDN.split(",");
        for (String component : dnComponents) {
            component = component.trim();
            if (component.startsWith("CN=")) {
                return component.substring(3);
            }
        }
        return null;
    }

	// "*.example.com" matches exactly one extra label: a.example.com (not example.com, not a.b.example.com)
	static boolean matchesDomain(String certDomain, String domain) {
		if(certDomain == null || domain == null){
			return false;
		}
		if(certDomain.startsWith("*.")){
			String wildCardBase = certDomain.substring(1).toLowerCase(Locale.ROOT); // ".example.com"
			String d = domain.toLowerCase(Locale.ROOT);
			return d.endsWith(wildCardBase)
					&& d.split("\\.").length == certDomain.split("\\.").length;
		}
		return certDomain.equalsIgnoreCase(domain);
	}
    
	/*
    private void httpsGet(String strURL) throws Exception
    {
        URL url = null;
        HttpsURLConnection con = null;
        
        try {
            url = new URL(strURL);
            ignoreSsl();
            con = (HttpsURLConnection) url.openConnection();
            con.setConnectTimeout(3000);
            con.getInputStream();

        }
        catch (UnknownHostException e){
        	Config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	rv.setResult("UnknownHostException occured");
        }
        catch (ConnectException e){
        	Config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	rv.setResult("ConnectException occured");
        }
        catch (SocketTimeoutException e){
        	Config.getLogger().log(Level.WARNING, e.getMessage(), e);
        	rv.setResult("SocketTimeoutException occured");
        }        
        catch (IOException e) {
        } 
        finally {
            if (con != null) {
                con.disconnect();
            }
        }
        
    }
    
    private void ignoreSsl() throws Exception{
        HostnameVerifier hv = new HostnameVerifier() {
        public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        };
        trustAllHttpsCertificates();
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }

    private void trustAllHttpsCertificates() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[1];
        TrustManager tm = new miTM();
        trustAllCerts[0] = tm;
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }
 
    class miTM implements TrustManager,X509TrustManager {

    	public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
 
        public boolean isServerTrusted(X509Certificate[] certs) {
            return true;
        }
 
        public boolean isClientTrusted(X509Certificate[] certs) {
            return true;
        }
 
        public void checkServerTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {

        	StringBuilder json = new StringBuilder();
            json.append("{\"domain\":\"" + domain + "\",\"certs\":[");

            int i = 0;
    		for(X509Certificate c:certs){
    			i++;
    			json.append("{");
    			json.append("\"index\":\""+Integer.toString(i)+"\",");
    			json.append("\"notafter\":\""+c.getNotAfter().toString()+"\",");
    			json.append("\"notbefore\":\""+c.getNotBefore().toString()+"\",");
    			json.append("\"serial\":\""+String.format("%032X", c.getSerialNumber())+"\",");
    			json.append("\"issuer\":\""+c.getIssuerDN().getName()+"\",");
                json.append("\"subject\":\""+c.getSubjectDN().getName()+"\"");
                json.append("}");
                if(i!=certs.length){
                	json.append(",");
                }
    		}
        
    		json.append("]}");
    		rv.setResult(json.toString());
    		rv.setOk(true);

            return;
        }
 
        public void checkClientTrusted(X509Certificate[] certs, String authType)
        		throws CertificateException {
        	return;
        }
    }
	*/
}
