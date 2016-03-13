package com.coinomi.stratumj.messages;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class CallMessage extends BaseMessage {

    protected CallMessage(String source) throws JSONException {
        super(source);
    }

    public CallMessage(String method, @Nullable List params) {
        super();
        setMethod(method);
        setParams(params);
    }

    public CallMessage(String method, String param) {
        super();
        setMethod(method);
        setParams(Arrays.asList(param));
    }

    public CallMessage(String method, int param) {
        super();
        setMethod(method);
        setParams(Arrays.asList(param));
    }

    public CallMessage(long id, String method, String param) {
        this(method, param);
        setId(id);
    }

    public CallMessage(long id, String method, List params) {
        this(method, params);
        setId(id);
    }

    public static CallMessage fromJson(String json) throws JSONException {
        return new CallMessage(json);
    }

    private void createParamsIfNeeded() {
        if (!has("params")) {
            try {
                put("params", new JSONArray());
            } catch (JSONException e) {
                // Should never happen because "params" is a valid JSON name
                throw new RuntimeException(e);
            }
        }
    }

    public String getMethod() {
        return optString("method", "");
    }

    public void setMethod(String method) {
        try {
            put("method", method);
        } catch (JSONException e) {
            // Should never happen because "method" is a valid JSON name
            throw new RuntimeException(e);
        }
    }

    public JSONArray getParams() {
        createParamsIfNeeded();
        try {
            return getJSONArray("params");
        } catch (JSONException e) {
            // Should never happen because we created the params
            return new JSONArray();
        }
    }

    public void setParam(String param) {
        setParams(Arrays.asList(param));
    }

    public void setParams(@Nullable Collection params) {
        if (params == null) return;
        try {
            put("params", params);
        } catch (JSONException e) {
            // Should never happen because "params" is a valid JSON name
            throw new RuntimeException(e);
        }
    }
//
//    public void addParams(Collection params) {
//        getParams().put(params);
//    }
//
//    public void addParam(String param) {
//        getParams().put(param);
//    }
}
