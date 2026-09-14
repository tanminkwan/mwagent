package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.File;
import java.util.logging.Level;

import org.json.simple.JSONObject;
import mwagent.common.SecurityValidator;

/**
 * Reads a file using an absolute path provided in additional_params.
 * 
 * Supported additional_params (String):
 * - The full absolute path to the file.
 * 
 * Example additional_params:
 * "/var/log/syslog" or "C:\\logs\\app.log"
 * 
 * Readable directories are the built-in defaults plus any path listed in the
 * "security.allowed_read_paths" property of agent.properties.
 */
public class ReadFullPathFile extends ReadFile {

	// Allowed base directories for reading files (extended by security.allowed_read_paths)
	private static final String[] DEFAULT_ALLOWED_READ_PATHS = {
		System.getProperty("user.dir"),           // Agent working directory
		System.getProperty("java.io.tmpdir"),     // Temp directory
		"/var/log",                                // Log directory (Linux)
		"/opt",                                    // Application directory (Linux)
		"C:\\logs",                                // Log directory (Windows)
		"C:\\Program Files"                        // Application directory (Windows)
	};

	public ReadFullPathFile(JSONObject command) {
		super(command);
	}

	protected String getFileFullName() {
		String requestedPath = commandVo.getAdditionalParams();

		// Security validation: check for path traversal and allowed directories (configurable, default ON)
		if (getConfig().isSecurityPathTraversalCheck()) {
			if (!SecurityValidator.isValidAbsolutePath(requestedPath, getAllowedReadPaths())) {
				getConfig().getLogger().severe("Security: Path traversal or unauthorized path detected: " + requestedPath);
				return null;  // Will cause FileNotFoundException, handled by parent class
			}
		}

		return requestedPath;
	}

	/**
	 * Built-in allowed directories plus the ones configured in agent.properties.
	 */
	protected String[] getAllowedReadPaths() {
		String[] configured = getConfig().getSecurityAllowedReadPaths();

		if (configured == null || configured.length == 0) {
			return DEFAULT_ALLOWED_READ_PATHS;
		}

		String[] merged = new String[DEFAULT_ALLOWED_READ_PATHS.length + configured.length];
		System.arraycopy(DEFAULT_ALLOWED_READ_PATHS, 0, merged, 0, DEFAULT_ALLOWED_READ_PATHS.length);
		System.arraycopy(configured, 0, merged, DEFAULT_ALLOWED_READ_PATHS.length, configured.length);

		return merged;
	}

	protected String getFileName(){
		return commandVo.getTargetFileName();
	}

	protected String getFilePath(){
		return commandVo.getAdditionalParams();
	}

}
