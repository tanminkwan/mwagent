package mwagent.common;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Security validation utilities for command execution and file path validation.
 * Prevents command injection and path traversal attacks.
 */
public class SecurityValidator {

    // Dangerous characters that could be used for command injection
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[;&|`$(){}\\[\\]<>\\n\\r]");

    // Path traversal patterns
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.[\\\\/]");

    /**
     * Validates command parameters to prevent command injection.
     * Checks for dangerous shell metacharacters.
     * If the parameter is in JSON format, validates each value within the JSON.
     *
     * @param param The parameter to validate
     * @return true if the parameter is safe, false otherwise
     */
    public static boolean isValidCommandParam(String param) {
        if (param == null || param.isEmpty()) {
            return true;
        }

        String trimmed = param.trim();
        // If it looks like JSON, parse and check each value
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
                org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser.parse(trimmed);
                for (Object key : json.keySet()) {
                    Object value = json.get(key);
                    if (value instanceof String) {
                        if (!isValidCommandParam((String) value)) {
                            return false;
                        }
                    } else if (value instanceof org.json.simple.JSONObject || value instanceof org.json.simple.JSONArray) {
                        if (!isValidCommandParam(value.toString())) {
                            return false;
                        }
                    }
                }
                return true;
            } catch (org.json.simple.parser.ParseException e) {
                // If parsing fails, fall back to normal check against DANGEROUS_CHARS
            }
        }

        return !DANGEROUS_CHARS.matcher(param).find();
    }

    /**
     * Sanitizes a command parameter by removing dangerous characters.
     *
     * @param param The parameter to sanitize
     * @return The sanitized parameter
     */
    public static String sanitizeCommandParam(String param) {
        if (param == null) {
            return "";
        }
        return DANGEROUS_CHARS.matcher(param).replaceAll("");
    }

    /**
     * Validates a file path to prevent path traversal attacks.
     *
     * @param basePath The base directory path
     * @param userPath The user-provided path to validate
     * @return true if the path is safe (within base directory), false otherwise
     */
    public static boolean isValidPath(String basePath, String userPath) {
        // Treat null as invalid, but empty as current directory
        if (userPath == null) {
            return false;
        }

        String normalizedUserPath = userPath.replace("\\", File.separator).replace("/", File.separator);
        if (normalizedUserPath.isEmpty()) {
            normalizedUserPath = ".";
        }

        // Check for obvious path traversal patterns
        if (PATH_TRAVERSAL.matcher(userPath).find() || PATH_TRAVERSAL.matcher(normalizedUserPath).find()) {
            return false;
        }

        try {
            File baseDir = new File(basePath).getCanonicalFile();
            File targetFile = new File(basePath, normalizedUserPath).getCanonicalFile();

            // Ensure the target is within the base directory
            return isWithin(baseDir, targetFile);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates an absolute file path to prevent path traversal attacks.
     * Checks if the path is within allowed directories.
     *
     * @param absolutePath The absolute path to validate
     * @param allowedBasePaths Array of allowed base directories
     * @return true if the path is within allowed directories, false otherwise
     */
    public static boolean isValidAbsolutePath(String absolutePath, String... allowedBasePaths) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return false;
        }

        // Check for path traversal patterns
        if (PATH_TRAVERSAL.matcher(absolutePath).find()) {
            return false;
        }

        try {
            File targetFile = new File(absolutePath).getCanonicalFile();

            for (String basePath : allowedBasePaths) {
                File baseDir = new File(basePath).getCanonicalFile();
                if (isWithin(baseDir, targetFile)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the canonical path, preventing path traversal.
     *
     * @param basePath The base directory
     * @param userPath The user-provided relative path
     * @return The validated canonical path
     * @throws SecurityException if path traversal is detected
     */
    public static String getValidatedPath(String basePath, String userPath) throws SecurityException {
        if (!isValidPath(basePath, userPath)) {
            throw new SecurityException("Path traversal detected or invalid path: " + userPath);
        }

        try {
            String normalizedUserPath = userPath == null ? "" : userPath.replace("\\", File.separator).replace("/", File.separator);
            if (normalizedUserPath.isEmpty()) {
                normalizedUserPath = ".";
            }
            return new File(basePath, normalizedUserPath).getCanonicalPath();
        } catch (IOException e) {
            throw new SecurityException("Invalid path: " + userPath, e);
        }
    }

    /**
     * Validates a filename (no directory components allowed).
     *
     * @param filename The filename to validate
     * @return true if it's a valid filename without path components
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // Check for path separators
        if (filename.contains("/") || filename.contains("\\")) {
            return false;
        }

        // Check for path traversal
        if (filename.equals("..") || filename.equals(".")) {
            return false;
        }

        return true;
    }

    /**
     * Checks that a canonical target is the base directory itself or below it.
     * Compares path components, so "/base2" is not treated as inside "/base".
     */
    private static boolean isWithin(File canonicalBase, File canonicalTarget) {
        return canonicalTarget.toPath().startsWith(canonicalBase.toPath());
    }

    /**
     * Resolves a zip entry name against the extraction directory (Zip Slip guard).
     *
     * @param destDir   The extraction directory
     * @param entryName The entry name taken from the archive
     * @return The file the entry should be written to
     * @throws IOException if the entry would be written outside destDir
     */
    public static File resolveZipEntry(File destDir, String entryName) throws IOException {
        String normalized = entryName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        File canonicalDest = destDir.getCanonicalFile();
        File target = new File(canonicalDest, normalized).getCanonicalFile();
        if (!isWithin(canonicalDest, target)) {
            throw new IOException("Zip entry is outside of the target dir: " + entryName);
        }
        return target;
    }
}
