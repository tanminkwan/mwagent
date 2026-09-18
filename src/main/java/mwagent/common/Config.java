package mwagent.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import mwagent.infrastructure.config.ConfigurationProvider;

/**
 * Configuration singleton implementing ConfigurationProvider interface.
 * Provides backward compatibility while supporting dependency injection.
 */
public final class Config implements ConfigurationProvider {

	private static final Config INSTANCE = new Config();

	private String agent_version = Version.VERSION;
	private String agent_type = "JAVAAGENT";

	private String hostName = "";
	private String userName = "";
	private String server_url = "";
	private String get_command_uri = "";
	private String post_agent_uri = "";
	private String agent_id = "";
	private String access_token = "";
	private String refresh_token = "";
	private String kafka_broker_address = "";
	private boolean mqtt_enabled = false;
	private String mqtt_broker_address = "";
	private String mqtt_credential = "";
	private long command_check_cycle = 60;

	// mTLS Configuration
	private boolean use_mtls = false;
	private String client_keystore_path = "";
	private String client_keystore_password = "";
	private String truststore_path = "";
	private String truststore_password = "";

	// Security Configuration
	private boolean security_command_injection_check = false;
	private boolean security_path_traversal_check = true;
	private String[] security_allowed_read_paths = new String[0];

	private Logger logger;
	
	private String os = "";	

	private Map<String, String> env = Collections.emptyMap();

	public static Config getConfig(){
		return INSTANCE;
	}
	
	public String getKafka_broker_address() {
		return kafka_broker_address;
	}
	public void setKafka_broker_address(String kafka_broker_address) {
		this.kafka_broker_address = kafka_broker_address;
	}

	/** MQTT 구독자 사용 여부. false 면 구독자를 아예 기동하지 않는다. */
	public boolean isMqtt_enabled() {
		return mqtt_enabled;
	}
	public void setMqtt_enabled(boolean mqtt_enabled) {
		this.mqtt_enabled = mqtt_enabled;
	}

	public String getMqtt_broker_address() {
		return mqtt_broker_address;
	}
	public void setMqtt_broker_address(String mqtt_broker_address) {
		this.mqtt_broker_address = mqtt_broker_address;
	}

	public String getMqtt_credential() {
		return mqtt_credential;
	}
	public void setMqtt_credential(String mqtt_credential) {
		this.mqtt_credential = mqtt_credential;
	}

	public Logger getLogger() {
		return logger;
	}
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	public Map<String, String> getEnv() {
		return env;
	}
	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
	public String getOs() {
		return os;
	}
	public void setOs(String os) {
		this.os = os;
	}

	public String getAgent_version() {
		return agent_version;
	}
	public String getAgent_type() {
		return agent_type;
	}

	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getServer_url() {
		return server_url;
	}
	public void setServer_url(String server_url) {
		this.server_url = server_url;
	}
	public String getGet_command_uri() {
		return get_command_uri;
	}
	public void setGet_command_uri(String get_command_uri) {
		this.get_command_uri = get_command_uri;
	}
	public String getPost_agent_uri() {
		return post_agent_uri;
	}
	public void setPost_agent_uri(String post_agent_uri) {
		this.post_agent_uri = post_agent_uri;
	}
	public String getAgent_id() {
		return agent_id;
	}
	public void setAgent_id(String agent_id) {
		this.agent_id = agent_id;
	}
	public String getAccess_token() {
		return access_token;
	}
	public void setAccess_token(String access_token) {
		this.access_token = access_token;
	}
	public String getRefresh_token() {
		return refresh_token;
	}
	public void setRefresh_token(String refresh_token) {
		this.refresh_token = refresh_token;
	}	
	public long getCommand_check_cycle() {
		return command_check_cycle;
	}
	public void setCommand_check_cycle(long command_check_cycle) {
		this.command_check_cycle = command_check_cycle;
	}

	// mTLS getters and setters
	public boolean isUseMtls() {
		return use_mtls;
	}
	public void setUseMtls(boolean use_mtls) {
		this.use_mtls = use_mtls;
	}
	public String getClientKeystorePath() {
		return client_keystore_path;
	}
	public void setClientKeystorePath(String client_keystore_path) {
		this.client_keystore_path = client_keystore_path;
	}
	public String getClientKeystorePassword() {
		return client_keystore_password;
	}
	public void setClientKeystorePassword(String client_keystore_password) {
		this.client_keystore_password = client_keystore_password;
	}
	@Override
	public String getTruststorePath() {
		return truststore_path;
	}
	public void setTruststorePath(String truststore_path) {
		this.truststore_path = truststore_path;
	}
	@Override
	public String getTruststorePassword() {
		return truststore_password;
	}
	public void setTruststorePassword(String truststore_password) {
		this.truststore_password = truststore_password;
	}

	// Security Configuration getters/setters
	public boolean isSecurityCommandInjectionCheck() {
		return security_command_injection_check;
	}
	public void setSecurityCommandInjectionCheck(boolean security_command_injection_check) {
		this.security_command_injection_check = security_command_injection_check;
	}
	public boolean isSecurityPathTraversalCheck() {
		return security_path_traversal_check;
	}
	public void setSecurityPathTraversalCheck(boolean security_path_traversal_check) {
		this.security_path_traversal_check = security_path_traversal_check;
	}
	/**
	 * Extra directories allowed for ReadFullPathFile, added to the built-in defaults.
	 */
	public String[] getSecurityAllowedReadPaths() {
		return security_allowed_read_paths;
	}
	/**
	 * @param allowed_read_paths comma separated absolute paths, e.g. "/sw/webtob,/usr/local/tomcat/logs"
	 */
	public void setSecurityAllowedReadPaths(String allowed_read_paths) {
		List<String> paths = new ArrayList<String>();
		if (allowed_read_paths != null) {
			for (String token : allowed_read_paths.split(",")) {
				String path = token.trim();
				if (!path.isEmpty()) {
					paths.add(path);
				}
			}
		}
		this.security_allowed_read_paths = paths.toArray(new String[0]);
	}

    public long setConfig() {

		Properties prop = new Properties();

		String host_name_var = "";
		String user_name_var = "";

		int rtn = 0;

		// Create default logger first (logs to current directory)
		try {
			Logger defaultLogger = Logger.getLogger("Hennry");
			defaultLogger.setLevel(Level.INFO);
			FileHandler defaultFh = new FileHandler(System.getProperty("user.dir") + File.separator + "mwagent.%u.%g.log", 1024*1024, 10, true);
			defaultFh.setFormatter(new SimpleFormatter());
			defaultLogger.addHandler(defaultFh);
			setLogger(defaultLogger);
		} catch (IOException e) {
			// If even default logger fails, use console logger
			Logger consoleLogger = Logger.getLogger("Hennry");
			consoleLogger.setLevel(Level.INFO);
			setLogger(consoleLogger);
		}

		try{

			FileReader in = new FileReader("agent.properties");
			prop.load(in);

			in.close();

			int command_check_cycle = Integer.parseInt(prop.getProperty("command_check_cycle", "60"));
			String get_command_uri = prop.getProperty("get_command_uri");
			String post_agent_uri = prop.getProperty("post_agent_uri");
			setRefresh_token(prop.getProperty("token"));
			host_name_var = prop.getProperty("host_name_var");
			user_name_var = prop.getProperty("user_name_var");
			String log_dir = prop.getProperty("log_dir", System.getProperty("user.dir"));
			String log_level = prop.getProperty("log_level", "INFO");

			setServer_url(prop.getProperty("server_url"));
			setCommand_check_cycle(command_check_cycle);
			setGet_command_uri(get_command_uri);
			setPost_agent_uri(post_agent_uri);
			setKafka_broker_address(prop.getProperty("kafka_broker_address", ""));

			// MQTT Configuration (Kafka 와 병행). 기본 비활성 — 명시적으로 켜야 동작한다
			setMqtt_enabled(Boolean.parseBoolean(prop.getProperty("mqtt_enabled", "false")));
			setMqtt_broker_address(prop.getProperty("mqtt_broker_address", ""));
			setMqtt_credential(prop.getProperty("mqtt_credential", ""));

			// mTLS Configuration
			setUseMtls(Boolean.parseBoolean(prop.getProperty("use_mtls", "false")));
			setClientKeystorePath(prop.getProperty("client.keystore.path", ""));
			setClientKeystorePassword(prop.getProperty("client.keystore.password", ""));
			setTruststorePath(prop.getProperty("truststore.path", ""));
			setTruststorePassword(prop.getProperty("truststore.password", ""));

			// Security Configuration (default: command injection check OFF, path traversal check ON)
			setSecurityCommandInjectionCheck(Boolean.parseBoolean(prop.getProperty("security.command_injection_check", "false")));
			setSecurityPathTraversalCheck(Boolean.parseBoolean(prop.getProperty("security.path_traversal_check", "true")));
			setSecurityAllowedReadPaths(prop.getProperty("security.allowed_read_paths", ""));

			// Reconfigure logger with settings from properties file
			Logger logger = getLogger();
			logger.setLevel(Level.parse(log_level));

			// Add file handler with configured log_dir if different from default
			if (!log_dir.equals(System.getProperty("user.dir"))) {
				// Check if log_dir exists
				File logDirFile = new File(log_dir);
				if (!logDirFile.exists() || !logDirFile.isDirectory()) {
					logger.severe("Log directory '" + log_dir + "' does not exist. Agent will terminate.");
					return -3;
				}
				FileHandler fh = new FileHandler(log_dir + File.separator + "mwagent.%u.%g.log", 1024*1024, 10, true);
				fh.setFormatter(new SimpleFormatter());
				logger.addHandler(fh);
			}

			getLogger().info("Logger is activated.");
			getLogger().info("MwManger Agent version: " + getAgent_version());

			//Get access token
			rtn = Common.updateToken();

			if(rtn < 0){
				getLogger().severe("Update Token error occurred.");
				System.exit(0);
			}

			getLogger().info("Access Token is updated.");

		}catch(IOException e){
			getLogger().log(Level.SEVERE, "Failed to load configuration. Please ensure 'agent.properties' file exists in the current directory.", e);
			return -1;
		}catch(IllegalArgumentException e){
			getLogger().log(Level.SEVERE, "Configuration error: " + e.getMessage(), e);
			return -2;
		}
		
    	Map<String, String> env = System.getenv();
    	for (String envName : env.keySet()){
    		if(envName.equals(host_name_var)){
    			setHostName(env.get(envName));
    		}else if(envName.equals(user_name_var)){
    			setUserName(env.get(envName));
    		} 
    	}
    	
    	setEnv(env);
    	
    	String agent_id = getHostName() + "_" + getUserName() + "_J";
    	setAgent_id(agent_id);
    	
    	getLogger().info(String.format("hostName:%s, userName:%s, get_command_uri:%s, command_check_cycle:%d", getHostName(), getUserName(), getGet_command_uri(), getCommand_check_cycle()));
		
		String os = System.getProperty("os.name").toLowerCase();
		
		getLogger().info(String.format("OS : %s %n", os));
		
		if (os.contains("win")){
			setOs("WIN");
		}else if (os.contains("aix")){
			setOs("AIX");
		}else if (os.contains("linux")){
			setOs("LINUX");
		}else if (os.contains("hp-ux")){
			setOs("HPUX");
		}
		
		getLogger().info(String.format("OS in Config : %s %n", getOs()));
		
		return 1;
		
    }
    
    /** Lock guarding read-modify-write of agent.properties (GetRefreshToken orders run on a thread pool). */
    private static final Object PROPERTY_FILE_LOCK = new Object();

    /**
     * Update a single key in agent.properties.
     *
     * Only the matching "key=value" line is rewritten; comments, ordering and the
     * other entries are preserved byte-for-byte. The result is written to a temp
     * file and atomically moved over the original so a crash or a concurrent
     * caller can never observe a truncated / half-written file.
     */
    public long updatePropertyLegacy(String item, String value){

        if (item == null || item.trim().isEmpty()) {
            getLogger().severe("updateProperty: key is null or empty.");
            return -2;
        }
        if (value == null) {
            getLogger().severe("updateProperty: value for '" + item + "' is null. Property file is left unchanged.");
            return -2;
        }

        synchronized (PROPERTY_FILE_LOCK) {
            try {
                updatePropertyInFile(new File("agent.properties"), item, value);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to update property '" + item + "' in agent.properties: " + e.getMessage(), e);
                return -1;
            }
        }

        return 1;

    }

    /**
     * Line-oriented, atomic rewrite of one property in a .properties file.
     * Package-private so it can be unit tested against a temp file.
     * Java 8 compatible (no Files.readString / String.isBlank etc).
     */
    static void updatePropertyInFile(File file, String key, String value) throws IOException {

        // ISO-8859-1 maps every byte to exactly one char and back, so all lines we do not
        // touch are rewritten byte-for-byte regardless of the platform default charset.
        // The new value is escaped to pure ASCII (backslash-u-XXXX for non-ASCII), which
        // Properties.load() decodes correctly under any charset used by setConfig().
        Charset cs = StandardCharsets.ISO_8859_1;

        String content = file.exists()
                ? new String(Files.readAllBytes(file.toPath()), cs)
                : "";

        String eol = content.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = content.isEmpty() ? new String[0] : content.split("\r?\n", -1);

        String newLine = key + "=" + escapePropertyValue(value);

        StringBuilder sb = new StringBuilder(content.length() + newLine.length() + 2);
        boolean replaced = false;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (!replaced && key.equals(parsePropertyKey(line))) {
                sb.append(newLine).append(eol);
                replaced = true;
                // Skip continuation lines (odd number of trailing backslashes) of the old entry
                while (hasContinuation(lines[i]) && i + 1 < lines.length) {
                    i++;
                }
                i++;
                continue;
            }
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append(eol);
            }
            i++;
        }

        if (!replaced) {
            if (sb.length() > 0 && !endsWith(sb, eol)) {
                sb.append(eol);
            }
            sb.append(newLine).append(eol);
        }

        writeAtomically(file, sb.toString(), cs);
    }

    /**
     * Write content through a temp file in the same directory and move it over the target, so a
     * crash or a concurrent reader can never observe a truncated / half-written file.
     */
    private static void writeAtomically(File file, String content, Charset cs) throws IOException {

        File dir = file.getAbsoluteFile().getParentFile();
        File tmp = File.createTempFile(file.getName() + ".", ".tmp", dir);
        try {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), cs)) {
                w.write(content);
                w.flush();
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }

    /**
     * Return the key of a properties line (per java.util.Properties syntax) or null for
     * blank / comment lines. Handles escaped separators inside the key.
     */
    static String parsePropertyKey(String line) {
        int len = line.length();
        int start = 0;
        while (start < len && isPropWhitespace(line.charAt(start))) {
            start++;
        }
        if (start >= len) {
            return null;
        }
        char first = line.charAt(start);
        if (first == '#' || first == '!') {
            return null;
        }
        StringBuilder key = new StringBuilder();
        for (int i = start; i < len; i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                if (i + 1 < len) {
                    key.append(line.charAt(++i)); // keep escaped char (e.g. "\:" -> ":")
                }
                continue;
            }
            if (c == '=' || c == ':' || isPropWhitespace(c)) {
                break;
            }
            key.append(c);
        }
        return key.toString();
    }

    private static boolean isPropWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    private static boolean hasContinuation(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean endsWith(StringBuilder sb, String suffix) {
        int n = suffix.length();
        return sb.length() >= n && sb.substring(sb.length() - n).equals(suffix);
    }

    /**
     * Escape a value so Properties.load() reads it back verbatim (same rules as
     * Properties.store()): backslash, line breaks, tabs and a leading space are
     * backslash-escaped, and anything outside printable ASCII becomes backslash-u-XXXX so the
     * output is charset independent. '=' ':' '#' '!' need no escaping inside a value.
     */
    static String escapePropertyValue(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\f': sb.append("\\f"); break;
                case ' ':
                    if (i == 0) { sb.append("\\ "); } else { sb.append(c); }
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c).toUpperCase();
                        for (int pad = hex.length(); pad < 4; pad++) { sb.append('0'); }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Apply a batch of deletes and upserts to agent.properties and return the resulting content
     * as an ordered key-value map. Backs the "set_properties" agent function.
     *
     * When both collections are empty nothing is written at all and the call degenerates into a
     * plain read, which is how set_properties implements its query mode.
     *
     * Filtering out protected keys such as "token" is the caller's responsibility.
     */
    public Map<String, String> applyAndReadProperties(List<String> deleteKeys,
                                                      Map<String, String> upsertPairs) throws IOException {
        return applyAndReadProperties(new File("agent.properties"), deleteKeys, upsertPairs);
    }

    /**
     * File-explicit variant of {@link #applyAndReadProperties(List, Map)}, used by the tests.
     *
     * Write and read-back happen under the same lock as the token update, so a concurrent
     * refresh can neither interleave with the rewrite nor change the file in between.
     */
    public static Map<String, String> applyAndReadProperties(File file, List<String> deleteKeys,
                                                             Map<String, String> upsertPairs) throws IOException {

        boolean hasDeletes = deleteKeys != null && !deleteKeys.isEmpty();
        boolean hasUpserts = upsertPairs != null && !upsertPairs.isEmpty();

        synchronized (PROPERTY_FILE_LOCK) {
            if (hasDeletes || hasUpserts) {
                applyPropertiesInFile(file, deleteKeys, upsertPairs);
            }
            return readAllProperties(file);
        }
    }

    /**
     * Line-oriented, atomic rewrite applying several deletes and upserts in a single pass.
     * Package-private so it can be unit tested against a temp file.
     *
     * Deleted keys lose every occurrence (continuation lines included). An upserted key keeps the
     * position of its first occurrence and any later duplicate is dropped, so the file ends up with
     * exactly one line per touched key - Properties.load() would otherwise let a stale duplicate
     * further down the file win. Keys absent from the file are appended at the end. Untouched
     * lines - comments, blanks and the other entries - are rewritten byte-for-byte, as in
     * updatePropertyInFile.
     *
     * Java 8 compatible (no Files.readString / String.isBlank etc).
     */
    static void applyPropertiesInFile(File file, List<String> deleteKeys,
                                      Map<String, String> upsertPairs) throws IOException {

        Charset cs = StandardCharsets.ISO_8859_1;

        String content = file.exists()
                ? new String(Files.readAllBytes(file.toPath()), cs)
                : "";

        String eol = content.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = content.isEmpty() ? new String[0] : content.split("\r?\n", -1);

        Set<String> deletes = new LinkedHashSet<String>();
        if (deleteKeys != null) {
            deletes.addAll(deleteKeys);
        }
        Map<String, String> upserts = new LinkedHashMap<String, String>();
        if (upsertPairs != null) {
            upserts.putAll(upsertPairs);
        }
        Set<String> written = new LinkedHashSet<String>();

        List<String> out = new ArrayList<String>(lines.length + upserts.size() + 1);

        int i = 0;
        while (i < lines.length) {
            String key = parsePropertyKey(lines[i]);
            if (key != null && (deletes.contains(key) || upserts.containsKey(key))) {
                if (upserts.containsKey(key) && written.add(key)) {
                    out.add(key + "=" + escapePropertyValue(upserts.get(key)));
                }
                // Drop the old entry together with its continuation lines
                while (hasContinuation(lines[i]) && i + 1 < lines.length) {
                    i++;
                }
                i++;
                continue;
            }
            out.add(lines[i]);
            i++;
        }

        if (written.size() < upserts.size()) {
            // split(..., -1) leaves a trailing "" when the file ends with a line break. Create one
            // if it is missing so appended entries never land on the last existing line.
            if (out.isEmpty() || !out.get(out.size() - 1).isEmpty()) {
                out.add("");
            }
            int insertAt = out.size() - 1;
            for (Map.Entry<String, String> entry : upserts.entrySet()) {
                if (!written.contains(entry.getKey())) {
                    out.add(insertAt++, entry.getKey() + "=" + escapePropertyValue(entry.getValue()));
                }
            }
        }

        StringBuilder sb = new StringBuilder(content.length() + 64);
        for (int n = 0; n < out.size(); n++) {
            sb.append(out.get(n));
            if (n < out.size() - 1) {
                sb.append(eol);
            }
        }

        writeAtomically(file, sb.toString(), cs);
    }

    /**
     * Read a .properties file into a map that keeps the order the keys appear in the file.
     * Values are decoded by java.util.Properties, so escapes and continuation lines are resolved.
     * Package-private so it can be unit tested against a temp file.
     */
    static Map<String, String> readAllProperties(File file) throws IOException {

        Map<String, String> ordered = new LinkedHashMap<String, String>();

        if (!file.exists()) {
            return ordered;
        }

        Properties prop = new Properties();
        InputStream in = new FileInputStream(file);
        try {
            prop.load(in);
        } finally {
            in.close();
        }

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        for (String line : content.split("\r?\n", -1)) {
            String key = parsePropertyKey(line);
            if (key != null && !ordered.containsKey(key) && prop.containsKey(key)) {
                ordered.put(key, prop.getProperty(key));
            }
        }

        // Keys whose name needed unescaping are not matched above; append them so nothing is lost.
        for (String name : prop.stringPropertyNames()) {
            if (!ordered.containsKey(name)) {
                ordered.put(name, prop.getProperty(name));
            }
        }

        return ordered;
    }

    // ========== ConfigurationProvider Interface Implementation ==========

    private Properties properties = new Properties();

    @Override
    public String getString(String key) {
        return properties.getProperty(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public int getInt(String key) {
        return getInt(key, 0);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public String getAgentId() {
        return agent_id;
    }

    @Override
    public String getAgentVersion() {
        return agent_version;
    }

    @Override
    public String getHostname() {
        return hostName;
    }

    @Override
    public String getUsername() {
        return userName;
    }

    @Override
    public String getAgentType() {
        return agent_type;
    }

    @Override
    public String getServerUrl() {
        return server_url;
    }

    @Override
    public String getCommandUri() {
        return get_command_uri;
    }

    @Override
    public String getResultUri() {
        return post_agent_uri;
    }

    @Override
    public String getAgentUri() {
        return post_agent_uri;
    }

    @Override
    public int getCommandCheckCycle() {
        return (int) command_check_cycle;
    }

    @Override
    public String getAccessToken() {
        return access_token;
    }

    @Override
    public void setAccessToken(String token) {
        this.access_token = token;
    }

    @Override
    public String getRefreshToken() {
        return refresh_token;
    }

    @Override
    public void setRefreshToken(String token) {
        this.refresh_token = token;
    }

    @Override
    public boolean isKafkaEnabled() {
        return kafka_broker_address != null && !kafka_broker_address.isEmpty();
    }

    @Override
    public String getKafkaBrokerAddress() {
        return kafka_broker_address;
    }

    @Override
    public void setKafkaBrokerAddress(String address) {
        this.kafka_broker_address = address;
    }

    @Override
    public boolean isMtlsEnabled() {
        return use_mtls;
    }

    @Override
    public String getKeystorePath() {
        return client_keystore_path;
    }

    @Override
    public String getKeystorePassword() {
        return client_keystore_password;
    }

    // getTruststorePath() and getTruststorePassword() are defined above with @Override

    @Override
    public boolean isCommandInjectionCheckEnabled() {
        return security_command_injection_check;
    }

    @Override
    public boolean isPathTraversalCheckEnabled() {
        return security_path_traversal_check;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return env;
    }

    @Override
    public void updateProperty(String key, String value) {
        updatePropertyLegacy(key, value);
    }

}
