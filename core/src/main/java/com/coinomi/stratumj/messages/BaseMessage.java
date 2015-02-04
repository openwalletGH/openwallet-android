package com.coinomi.stratumj.messages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class BaseMessage extends JSONObject {

    protected  BaseMessage(String source) throws JSONException {
        super(source);
    }

    public BaseMessage() {
        setId(-1);
    }

    public BaseMessage(long id) {
        setId(id);
    }

    public static BaseMessage fromJson(String json) throws JSONException {
        return new BaseMessage(json);
    }

    public long getId() {
        return optLong("id", -1);
    }

    public void setId(long id) {
        try {
            if (id < 0) {
                put("id", JSONObject.NULL);
            } else {
                put("id", id);
            }
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

    public JSONObject put(String key, @Nullable Collection value) throws JSONException {
        this.put(key, value != null ? new JSONArray(value) : new JSONArray());
        return this;
    }

    @Override
    public String toString() {
        return super.toString() + '\n';
    }
}
