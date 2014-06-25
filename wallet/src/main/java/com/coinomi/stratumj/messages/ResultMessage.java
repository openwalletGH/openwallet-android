package com.coinomi.stratumj.messages;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * @author Giannis Dzegoutanis
 */
public class ResultMessage extends BaseMessage {
	public ResultMessage(String source) throws JSONException {
		super(source);
	}

	public JSONArray getResult() {
		if (has("result")) {
			if (opt("result") instanceof JSONArray) {
                try {
                    return getJSONArray("result");
                } catch (JSONException e) {
					// Should not happen
					throw new RuntimeException(e);
                }
            }
			else {
				JSONArray result = new JSONArray();
				try {
					result.put(get("result"));
				} catch (JSONException e) {
					// Should not happen
					throw new RuntimeException(e);
				}
                return result;
			}
		}
		else {
			return new JSONArray();
		}
	}
}
