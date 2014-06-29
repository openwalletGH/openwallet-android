package com.coinomi.stratumj.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Giannis Dzegoutanis
 */
public class BaseMessage extends JSONObject {

	public BaseMessage(long id) {
		setId(id);
	}

	public BaseMessage(String source) throws JSONException {
		super(source);
	}

	public long getId() {
		return optLong("id", -1);
	}

	public void setId(long id) {
		try {
			put("id", id);
		} catch (JSONException e) {
			// Should never happen because "id" is a valid JSON name
			throw new RuntimeException(e);
		}
	}

	public boolean isResult() {
		return has("result");
	}

	public boolean isCall() {
		return has("method");
	}

	public boolean errorOccured() {
		return !optString("error").equals("");
	}

	public String getError() {
		return optString("error");
	}

	public String getFailedRequest() {
		return optString("request");
	}

	@Override
	public String toString() {
		return super.toString() + '\n';
	}
}
