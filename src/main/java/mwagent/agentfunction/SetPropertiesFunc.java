package mwagent.agentfunction;

import static mwagent.common.Config.getConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mwagent.common.Common;
import mwagent.common.Config;
import mwagent.vo.AgentErrorCode;
import mwagent.vo.CommandVO;
import mwagent.vo.ResultVO;

/**
 * Updates agent.properties on behalf of mw-app and returns the resulting configuration.
 *
 * additional_params (JSON):
 * {
 *   "delete": ["item1", "item2"],
 *   "upsert": [{"item3": "value3"}, {"item4": "value4"}]
 * }
 *
 * - delete : remove the item, ignored when the item does not exist
 * - upsert : update the item when it exists, insert it otherwise
 * - both absent or empty: nothing is written and the current configuration is returned (query mode)
 *
 * "token" is rejected in either list: the refresh token is what proves this agent's identity to
 * mw-app, so letting the command channel read or change it would turn a hijacked channel into an
 * authentication bypass. Every other item, sensitive ones included, may be changed and is returned.
 *
 * Validation runs in full before the file is touched, so a request either applies completely or
 * not at all - mw-app never has to guess which half of a request took effect.
 *
 * On success result holds the whole agent.properties as a JSON object with "token" removed;
 * on failure it holds "Error:CODE - message" and isOk is false.
 */
public class SetPropertiesFunc implements AgentFunc {

	/** The only item that can neither be read nor written through this function. */
	static final String PROTECTED_KEY = "token";

	static final String INVALID_PARAMS = "INVALID_PARAMS";
	static final String TOKEN_NOT_ALLOWED = "TOKEN_NOT_ALLOWED";
	static final String CONFLICTING_KEYS = "CONFLICTING_KEYS";
	static final String DUPLICATED_KEYS = "DUPLICATED_KEYS";
	static final String FILE_WRITE_ERROR = "FILE_WRITE_ERROR";

	/** Keys are written unescaped, so these would silently break the "key=value" line layout. */
	private static final String ILLEGAL_KEY_CHARS = "=:#!\\";

	@Override
	public ArrayList<ResultVO> exeCommand(CommandVO command) {

		ResultVO rv = new ResultVO();
		rv.setOk(false);

		List<String> deleteKeys = new ArrayList<String>();
		Map<String, String> upsertPairs = new LinkedHashMap<String, String>();

		try {
			JSONObject params = command.getAdditionalParamsJson();
			if (params == null) {
				throw new ParamError(INVALID_PARAMS,
						"additional_params is missing or is not a valid JSON object.");
			}
			parseParams(params, deleteKeys, upsertPairs);
		} catch (ParamError e) {
			getConfig().getLogger().warning("set_properties: " + e.toResultText()
					+ " (" + e.agentErrorCode().getCode() + ")");
			rv.setResult(e.toResultText());
			return Common.makeOneResultArray(rv, command);
		}

		Map<String, String> properties;
		try {
			properties = applyAndRead(deleteKeys, upsertPairs);
		} catch (Exception e) {
			String message = "Error:" + FILE_WRITE_ERROR
					+ " - Failed to update agent.properties: " + e.getMessage();
			getConfig().getLogger().log(Level.SEVERE, "set_properties: " + message
					+ " (" + AgentErrorCode.FILE_WRITE_ERROR.getCode() + ")", e);
			rv.setResult(message);
			return Common.makeOneResultArray(rv, command);
		}

		// The refresh token never leaves the agent, not even as a masked placeholder: mw-app must
		// see a configuration in which the item simply does not exist.
		properties.remove(PROTECTED_KEY);

		// Values may be passwords or credentials, so only item names are logged.
		if (deleteKeys.isEmpty() && upsertPairs.isEmpty()) {
			getConfig().getLogger().info("set_properties: query mode, agent.properties left untouched.");
		} else {
			getConfig().getLogger().info("set_properties: deleted=" + deleteKeys
					+ ", upserted=" + upsertPairs.keySet());
		}

		rv.setOk(true);
		rv.setResult(JSONObject.toJSONString(properties));
		return Common.makeOneResultArray(rv, command);
	}

	/**
	 * Seam over the file access so unit tests can run against a temp file instead of the
	 * agent.properties of the working directory.
	 */
	Map<String, String> applyAndRead(List<String> deleteKeys, Map<String, String> upsertPairs)
			throws Exception {
		return Config.getConfig().applyAndReadProperties(deleteKeys, upsertPairs);
	}

	/**
	 * Validate the whole request and collect the item names to delete and the pairs to upsert.
	 * Throws on the first problem found, before any file is opened.
	 */
	private void parseParams(JSONObject params, List<String> deleteKeys, Map<String, String> upsertPairs)
			throws ParamError {

		for (Object field : params.keySet()) {
			if (!"delete".equals(field) && !"upsert".equals(field)) {
				getConfig().getLogger().warning("set_properties: ignoring unknown field '" + field + "'.");
			}
		}

		Set<String> deletes = new LinkedHashSet<String>();

		Object deleteObj = params.get("delete");
		if (deleteObj != null) {
			if (!(deleteObj instanceof JSONArray)) {
				throw new ParamError(INVALID_PARAMS, "'delete' must be a JSON array of item names.");
			}
			for (Object element : (JSONArray) deleteObj) {
				if (!(element instanceof String)) {
					throw new ParamError(INVALID_PARAMS, "'delete' must contain item names as strings.");
				}
				String key = validateKey((String) element);
				if (!deletes.add(key)) {
					throw new ParamError(DUPLICATED_KEYS, "'" + key + "' is specified more than once.");
				}
			}
		}

		Object upsertObj = params.get("upsert");
		if (upsertObj != null) {
			if (!(upsertObj instanceof JSONArray)) {
				throw new ParamError(INVALID_PARAMS, "'upsert' must be a JSON array of objects.");
			}
			for (Object element : (JSONArray) upsertObj) {
				if (!(element instanceof JSONObject)) {
					throw new ParamError(INVALID_PARAMS,
							"'upsert' element must be a JSON object with exactly one key.");
				}
				JSONObject pair = (JSONObject) element;
				if (pair.size() != 1) {
					throw new ParamError(INVALID_PARAMS,
							"'upsert' element must be a JSON object with exactly one key.");
				}
				Object rawKey = pair.keySet().iterator().next();
				String key = validateKey(rawKey == null ? "" : rawKey.toString());
				Object value = pair.get(rawKey);
				if (value == null) {
					throw new ParamError(INVALID_PARAMS, "value for '" + key + "' is null.");
				}
				if (upsertPairs.put(key, value.toString()) != null) {
					throw new ParamError(DUPLICATED_KEYS, "'" + key + "' is specified more than once.");
				}
			}
		}

		for (String key : upsertPairs.keySet()) {
			if (deletes.contains(key)) {
				throw new ParamError(CONFLICTING_KEYS, "'" + key + "' appears in both delete and upsert.");
			}
		}

		deleteKeys.addAll(deletes);
	}

	private String validateKey(String rawKey) throws ParamError {

		String key = rawKey.trim();

		if (key.isEmpty()) {
			throw new ParamError(INVALID_PARAMS, "item name must not be empty.");
		}
		if (PROTECTED_KEY.equalsIgnoreCase(key)) {
			throw new ParamError(TOKEN_NOT_ALLOWED, "'token' cannot be deleted or upserted.");
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c == '\r' || c == '\n') {
				throw new ParamError(INVALID_PARAMS, "item name must not contain a line break.");
			}
			if (c <= ' ' || ILLEGAL_KEY_CHARS.indexOf(c) >= 0) {
				throw new ParamError(INVALID_PARAMS,
						"item name '" + key + "' contains an illegal character '" + c + "'.");
			}
		}

		return key;
	}

	/**
	 * A rejected request. Reported back as a failed ResultVO rather than thrown out of exeCommand:
	 * ExeAgentFunc turns an escaping exception into rtn = -1 and then OrderCaller skips
	 * sendResults(), so mw-app would never learn that the request failed.
	 */
	private static final class ParamError extends Exception {

		private static final long serialVersionUID = 1L;

		private final String code;

		ParamError(String code, String message) {
			super(message);
			this.code = code;
		}

		String toResultText() {
			return "Error:" + code + " - " + getMessage();
		}

		/** Maps the result code onto the agent-wide catalogue, for the log line only. */
		AgentErrorCode agentErrorCode() {
			return TOKEN_NOT_ALLOWED.equals(code)
					? AgentErrorCode.CONFIG_PROTECTED_KEY
					: AgentErrorCode.CMD_INVALID_PARAMS;
		}
	}

}
