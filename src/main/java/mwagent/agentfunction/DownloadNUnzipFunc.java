package mwagent.agentfunction;

import java.util.ArrayList;

import org.json.simple.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import static mwagent.common.Config.getConfig;


import mwagent.common.Common;
import mwagent.common.LogSafe;
import mwagent.common.SecurityValidator;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;

/**
 * Downloads a file from a URL and optionally extracts it if it's a zip.
 * 
 * Supported additional_params (JSON):
 * - url: (String) The source URL to download from.
 * - target_directory: (String, optional) Local directory to save the file. Defaults to ".".
 * - backup_if_exists: (Boolean, optional) Whether to backup existing file. Defaults to true.
 * - extract: (Boolean, optional) Whether to extract if it's a zip. Defaults to true.
 * 
 * Example additional_params JSON:
 * {
 *   "url": "http://example.com/file.zip",
 *   "target_directory": "/tmp/downloads",
 *   "backup_if_exists": true,
 *   "extract": true
 * }
 */
public class DownloadNUnzipFunc implements AgentFunc {

	private String downloadUrl = "";
	private String targetDirectory = ".";
	private Boolean backupIfExists = true;
	private Boolean extract = true;
	
	@Override
	public ArrayList<ResultVO> exeCommand(CommandVO command) {

		getConfig().getLogger().info("AdditionalParams : " + LogSafe.safe(command.getAdditionalParams()));

		ResultVO rv = new ResultVO();
		rv.setOk(false);

		JSONObject paramsJson = command.getAdditionalParamsJson();
		if (paramsJson == null) {
			rv.setResult("params parsing error: JSON is null or invalid");
			return Common.makeOneResultArray(rv, command);
		}

		int rtn = setParams(paramsJson);
		
		if(rtn < 0){
            rv.setResult("params parsing error");
            return Common.makeOneResultArray(rv, command);
        }

		// Security validation for target directory
		String baseDir = System.getProperty("user.dir");
		if (getConfig().isSecurityPathTraversalCheck()) {
			if (!SecurityValidator.isValidPath(baseDir, targetDirectory)) {
				getConfig().getLogger().severe("Security: Path traversal detected in target_directory: " + targetDirectory);
				rv.setResult("Error:SecurityException - Invalid path");
				return Common.makeOneResultArray(rv, command);
			}
		}
		
        try {
        	
        	File savedFile = downloadFile(downloadUrl, targetDirectory, backupIfExists);
        	getConfig().getLogger().info("[INFO] Downloaded file: " + savedFile.getAbsolutePath());
			
			if (extract && savedFile.getName().toLowerCase().endsWith(".zip")) {
                unzipFile(savedFile, new File(targetDirectory)); 
                getConfig().getLogger().info("[INFO] Unzip completed.");
            } else {
            	getConfig().getLogger().info("[INFO] Download completed (extract skipped or not a zip).");
            }
			
        } catch (IOException e) {
            getConfig().getLogger().severe(e.getMessage());
            rv.setResult("Error:" + e.getMessage());
            return Common.makeOneResultArray(rv, command);
        }
        
		rv.setOk(true);
		rv.setResult("File download & unzip is complited.");
    	return Common.makeOneResultArray(rv, command);
		
	}
	
	private int setParams(JSONObject jsonObj){
		
        try {
            setDownloadUrl((String) jsonObj.get("url"));
            if(jsonObj.containsKey("target_directory")){
            	setTargetDirectory((String) jsonObj.get("target_directory"));
            }
            if(jsonObj.containsKey("backup_if_exists")){
            	setBackupIfExists((Boolean) jsonObj.get("backup_if_exists"));
            }
            if(jsonObj.containsKey("extract")){
            	setExtract((Boolean) jsonObj.get("extract"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            getConfig().getLogger().severe(e.getMessage());
            return -1;
        }
        
        return 1;
		
	}
	
    /**
     * Downloads a file from the given pre-signed URL and saves it in the specified directory.
     * If a file with the same name already exists, it will be backed up (renamed with .bak)
     * if backupIfExists is true, otherwise it will be overwritten.
     *
     * @param presignedUrl   the pre-signed URL to download from
     * @param targetDirectory the path of the local directory to save the file
     * @param backupIfExists  whether to back up the existing file (true) or overwrite it (false)
     * @return               the File object representing the downloaded file
     * @throws IOException   if a network or I/O error occurs
     */
    private File downloadFile(String presignedUrl, String targetDirectory, boolean backupIfExists) throws IOException {

    	HttpURLConnection conn = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;

        try {
            // 1) Create URL object and open connection
            URL url = new URL(presignedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // If needed, set authorization headers here
            // conn.setRequestProperty("Authorization", "Bearer <TOKEN>");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download file. HTTP code: " + responseCode);
            }

            // 2) Handle directory creation
            File dir = new File(targetDirectory);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    System.err.println("[WARN] Failed to create directories: " + dir.getAbsolutePath());
                }
            }

            String disposition = conn.getHeaderField("Content-Disposition");
            String fileName = getFileNameFromContentDisposition(disposition);
            if (fileName == null || fileName.trim().isEmpty()) {
            
				fileName = "downloaded_file";
            }
            // The name comes from the server response; never let it leave targetDirectory
            if (!SecurityValidator.isValidFilename(fileName)) {
                throw new IOException("Invalid file name in Content-Disposition: " + fileName);
            }
            // Final file object
            File outFile = new File(dir, fileName);

            // 3) Back up the existing file if backupIfExists == true
            if (outFile.exists() && backupIfExists) {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String backupName = fileName + "_" + timeStamp + ".bak";
                File backupFile = new File(dir, backupName);

                boolean renamed = outFile.renameTo(backupFile);
                if (renamed) {
                    System.out.println("[INFO] Existing file backed up as " + backupFile.getAbsolutePath());
                } else {
                    System.err.println("[WARN] Failed to back up the existing file. Overwriting directly...");
                }
            }

            // 4) Download the stream to the file
            bis = new BufferedInputStream(conn.getInputStream());
            fos = new FileOutputStream(outFile); // Overwrite mode

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();

            return outFile;

        } finally {
            // Resource cleanup
            if (fos != null) {
                fos.close();
            }
            if (bis != null) {
                bis.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
	
    private String getFileNameFromContentDisposition(String disposition) {
        if (disposition == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");
        Matcher matcher = pattern.matcher(disposition);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
	
    public static void unzipFile(File zipFile, File destDir) throws IOException {

        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Rejects entries that escape destDir (Zip Slip)
                File outFile = SecurityValidator.resolveZipEntry(destDir, entry.getName());

                if (entry.isDirectory()) {
                    
					if (!outFile.exists()) {
                        outFile.mkdirs();
                    }
                } else {
                    
					File parentDir = outFile.getParentFile();
                    if (!parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            if (zis != null) {
                zis.close();
            }
        }
    }
    
	private void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}

	private void setTargetDirectory(String targetDirectory) {
		this.targetDirectory = targetDirectory;
	}

	private void setBackupIfExists(Boolean backupIfExists) {
		this.backupIfExists = backupIfExists;
	}

	private void setExtract(Boolean extract) {
		this.extract = extract;
	}

}
