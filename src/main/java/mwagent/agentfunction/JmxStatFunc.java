package mwagent.agentfunction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import mwagent.common.Common;
import static mwagent.common.Config.getConfig;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;

/**
 * Collects JMX statistics from a JEUS server.
 * 
 * Note: This function unexpectedly uses 'target_file_path' to pass a JSON string of parameters.
 * 
 * Expected JSON Fields (passed via target_file_path):
 * - jmx_url: (String) JMX provider URL (e.g., "service:jmx:rmi:///jndi/jeus://host:port/ManagementServer").
 * - jmx_username: (String) JMX login username.
 * - jmx_password: (String) JMX login password.
 * - jmx_target: (String) JEUS target name.
 * - jmx_domain: (String) JEUS domain name.
 * 
 * Example JSON string:
 * {
 *   "jmx_url": "jeus://127.0.0.1:9736",
 *   "jmx_username": "administrator",
 *   "jmx_password": "password",
 *   "jmx_target": "adminServer",
 *   "jmx_domain": "jeus_domain"
 * }
 */
public class JmxStatFunc implements AgentFunc {

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<ResultVO> exeCommand(CommandVO command) throws IOException {

    	getConfig().getLogger().info("JmxStatFunc is starting.");
        String params = command.getTargetFilePath();

        String jmx_url = "";
        String jmx_username = "";
        String jmx_password = "";
        String jmx_target = "";
        String jmx_domain = "";
        JMXConnector connector = null;

        ResultVO rv = new ResultVO();
        rv.setOk(false);

        try {
            JSONParser jsonPar = new JSONParser();
            JSONObject jsonObj = (JSONObject) jsonPar.parse(params);

            jmx_url = (String) jsonObj.get("jmx_url");
            jmx_username = (String) jsonObj.get("jmx_username");
            jmx_password = (String) jsonObj.get("jmx_password");
            jmx_target = (String) jsonObj.get("jmx_target");
            jmx_domain = (String) jsonObj.get("jmx_domain");

        } catch (Exception e) {
            getConfig().getLogger().severe(e.getMessage());
            rv.setResult("params parsing error");
            return Common.makeOneResultArray(rv, command);
        }

        // jmx_target goes into a JNDI name and, with jmx_domain, into an ObjectName; allow plain names only
        if (!isValidJmxName(jmx_target) || !isValidJmxName(jmx_domain)) {
            getConfig().getLogger().severe("Security: Invalid jmx_target or jmx_domain");
            rv.setResult("Error:SecurityException - Invalid jmx_target or jmx_domain");
            return Common.makeOneResultArray(rv, command);
        }

        try {
            // 1) JEUS 환경 설정
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "jeus.jndi.JNSContextFactory");
            env.put(Context.PROVIDER_URL, jmx_url);
            env.put(Context.SECURITY_PRINCIPAL, jmx_username);
            env.put(Context.SECURITY_CREDENTIALS, jmx_password);
            
            InitialContext ctx = new InitialContext(env);

            for (Entry<?, ?> a : ctx.getEnvironment().entrySet()) {
            	// never log the JMX password
            	Object value = Context.SECURITY_CREDENTIALS.equals(a.getKey()) ? "***" : a.getValue();
            	getConfig().getLogger().fine("ctx 2 : " + a.getKey() + ":" + value);
            }

            connector = (JMXConnector) ctx.lookup("mgmt/rmbs/" + jmx_target);
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();

            ObjectName objectName = new ObjectName(
                    "JEUS:j2eeType=J2EEDomain,JMXManager=" + jmx_target + ",name=" + jmx_domain);

            // =============================
            // 2) Reflection 으로 J2EEDomainMBean 로딩
            //    (import jeus.management.j2ee.J2EEDomainMBean 제거)
            // =============================
            Class<?> domainMBeanClass = Class.forName("jeus.management.j2ee.J2EEDomainMBean");

            // 3) Proxy 생성 시, Class<?> 객체 사용
            Object domainMBeanProxy = MBeanServerInvocationHandler.newProxyInstance(
                    mbeanServer,
                    objectName,
                    domainMBeanClass,
                    false
            );

            // 4) Reflection으로 메서드 가져오기
            //    - List<String> getServerListFromDescriptor()
            //    - String getServerState(String serverName)
            //    메서드 시그니처를 찾아서 invoke
            java.lang.reflect.Method getServerListMethod = domainMBeanClass.getMethod("getServerListFromDescriptor");
            java.lang.reflect.Method getServerStateMethod = domainMBeanClass.getMethod("getServerState", String.class);

            // 5) getServerListFromDescriptor() 호출
            Object resultObj = getServerListMethod.invoke(domainMBeanProxy);

            List<String> servers = (List<String>) resultObj;

            // 6) 결과 JSON 작성
            JSONArray severArray = new JSONArray();
            
            for(String s: servers) {
            	JSONObject serverObj = new JSONObject();
            	serverObj.put("server_name", s);

                // getServerState(serverName) 호출
                String state = (String) getServerStateMethod.invoke(domainMBeanProxy, s);
                serverObj.put("status", state);
                
                severArray.add(serverObj);
            }
            
            String resultJson = severArray.toJSONString();
            rv.setResult(resultJson);
            rv.setObjectAggregationKey(getConfig().getAgent_id() + "__" + jmx_domain);
            rv.setOk(true);

            connector.close();

        } catch (javax.naming.ServiceUnavailableException e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            rv.setResult("jmx connection error : " + jmx_url);
        } catch (Exception e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            rv.setResult("jmx error");
            if (connector != null) {
                connector.close();
            }
        }

        return Common.makeOneResultArray(rv, command);
    }

    // JEUS server / domain names: letters, digits, "_", "-", "."
    static boolean isValidJmxName(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]+");
    }
}
