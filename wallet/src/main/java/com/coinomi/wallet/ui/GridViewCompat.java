package com.coinomi.wallet.ui;

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The fragments of code has been used from AbsListView.java of Android SDK
 *
 * @author Paramvir Bali
 * @mail paramvir@rokoder.com
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.coinomi.wallet.R;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class is based on the GridView. We need to create this class as multiselection comes in API
 * 10 into GridView. So lot of code is copied from the Android Source Code. APIs which we are
 * implementing here are suffixed with 'C' to avoid recursion which when you try to call the API
 * using reflection. So we made the signature different.
 *
 * <pre>
 * Ref code:
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/widget/GridView.java
 * </pre>
 */
public class GridViewCompat extends GridView {
    private static final String TAG = GridViewCompat.class.getSimpleName();
    /**
     * Running count of how many items are currently checked
     */
    int mCheckedItemCountC;
    /**
     * Running state of which positions are currently checked
     */
    SparseBooleanArray mCheckStatesC;
    /**
     * Running state of which IDs are currently checked. If there is a value for a given key, the
     * checked state for that ID is true and the value holds the last known position in the adapter
     * for that id.
     */
    LongSparseArray<Integer> mCheckedIdStatesC;
    /**
     * Controls if/how the user may choose/check items in the list
     */
    int mChoiceModeC = ListView.CHOICE_MODE_NONE;

    /**
     * A copy of mNumColumns property for older APIs that don't support getNumColumns
     */
    private int copyNumColumns;

    /**
     * Variables for backward compatibility
     */
    private static boolean inCompatibleMode = false;
    private static Method gridView_getCheckedItemIds;
    private static Method gridView_isItemChecked;
    private static Method gridView_getCheckedItemPosition;
    private static Method gridView_getCheckedItemPositions;
    private static Method gridView_clearChoices;
    private static Method gridView_setItemChecked;
    private static Method gridView_setChoiceMode;
    private static Method gridView_getChoiceMode;
    private static Method gridView_getCheckedItemCount;

    static {
        try {
            inCompatibleMode = false;

            gridView_getChoiceMode = GridView.class.getMethod("getChoiceMode", (Class<?>[]) null);
            gridView_getCheckedItemIds =
                    GridView.class.getMethod("getCheckedItemIds", (Class<?>[]) null);
            gridView_isItemChecked =
                    GridView.class.getMethod("isItemChecked", new Class[] {
                            int.class
                    });
            gridView_getCheckedItemPosition =
                    GridView.class.getMethod("getCheckedItemPosition", (Class<?>[]) null);
            gridView_getCheckedItemPositions =
                    GridView.class.getMethod("getCheckedItemPositions", (Class<?>[]) null);
            gridView_clearChoices = GridView.class.getMethod("clearChoices", (Class<?>[]) null);
            gridView_setItemChecked =
                    GridView.class.getMethod("setItemChecked", new Class[] {
                            int.class,
                            boolean.class
                    });
            gridView_setChoiceMode =
                    GridView.class.getMethod("setChoiceMode", new Class[] {
                            int.class
                    });
            gridView_getCheckedItemCount =
                    GridView.class.getMethod("getCheckedItemCount", (Class<?>[]) null);

        } catch (NoSuchMethodException e) {
            Log.d(TAG, "Running in compatibility mode as '" + e.getMessage() + "' not found");
            // If any of the method is missing, we are in compatibility mode
            inCompatibleMode = true;
            gridView_getCheckedItemIds = null;
            gridView_isItemChecked = null;
            gridView_getCheckedItemPosition = null;
            gridView_getCheckedItemPositions = null;
            gridView_clearChoices = null;
            gridView_setItemChecked = null;
            gridView_setChoiceMode = null;
            gridView_getChoiceMode = null;
            gridView_getCheckedItemCount = null;
        }

    }

    /**
     * SparseArrays map longs to Objects. Unlike a normal array of Objects, there can be gaps in the
     * indices. It is intended to be more efficient than using a HashMap to map Longs to Objects.
     *
     * <pre>
     * Source : https://github.com/android/platform_frameworks_base/blob/master/core/java/android/util/LongSparseArray.java
     * </pre>
     *
     * @hide
     */
    private static class LongSparseArray<E> {
        private static final Object DELETED = new Object();
        private boolean mGarbage = false;

        /**
         * Creates a new SparseArray containing no mappings.
         */
        public LongSparseArray() {
            this(10);
        }

        /**
         * Creates a new SparseArray containing no mappings that will not require any additional
         * memory allocation to store the specified number of mappings.
         */
        public LongSparseArray(int initialCapacity) {
            initialCapacity = ArrayUtils.idealIntArraySize(initialCapacity);

            mKeys = new long[initialCapacity];
            mValues = new Object[initialCapacity];
            mSize = 0;
        }

        /**
         * @return A copy of all keys contained in the sparse array.
         */
        public long[] getKeys() {
            int length = mKeys.length;
            long[] result = new long[length];
            System.arraycopy(mKeys, 0, result, 0, length);
            return result;
        }

        /**
         * Sets all supplied keys to the given unique value.
         *
         * @param keys Keys to set
         * @param uniqueValue Value to set all supplied keys to
         */
        public void setValues(long[] keys, E uniqueValue) {
            int length = keys.length;
            for (int i = 0; i < length; i++) {
                put(keys[i], uniqueValue);
            }
        }

        /**
         * Gets the Object mapped from the specified key, or <code>null</code> if no such mapping
         * has been made.
         */
        public E get(long key) {
            return get(key, null);
        }

        /**
         * Gets the Object mapped from the specified key, or the specified Object if no such mapping
         * has been made.
         */
        public E get(long key, E valueIfKeyNotFound) {
            int i = binarySearch(mKeys, 0, mSize, key);

            if (i < 0 || mValues[i] == DELETED) {
                return valueIfKeyNotFound;
            } else {
                return (E) mValues[i];
            }
        }

        /**
         * Removes the mapping from the specified key, if there was any.
         */
        public void delete(long key) {
            int i = binarySearch(mKeys, 0, mSize, key);

            if (i >= 0) {
                if (mValues[i] != DELETED) {
                    mValues[i] = DELETED;
                    mGarbage = true;
                }
            }
        }

        /**
         * Alias for {@link #delete(long)}.
         */
        public void remove(long key) {
            delete(key);
        }

        private void gc() {
            // Log.e("SparseArray", "gc start with " + mSize);

            int n = mSize;
            int o = 0;
            long[] keys = mKeys;
            Object[] values = mValues;

            for (int i = 0; i < n; i++) {
                Object val = values[i];

                if (val != DELETED) {
                    if (i != o) {
                        keys[o] = keys[i];
                        values[o] = val;
                    }

                    o++;
                }
            }

            mGarbage = false;
            mSize = o;

            // Log.e("SparseArray", "gc end with " + mSize);
        }

        /**
         * Adds a mapping from the specified key to the specified value, replacing the previous
         * mapping from the specified key if there was one.
         */
        public void put(long key, E value) {
            int i = binarySearch(mKeys, 0, mSize, key);

            if (i >= 0) {
                mValues[i] = value;
            } else {
                i = ~i;

                if (i < mSize && mValues[i] == DELETED) {
                    mKeys[i] = key;
                    mValues[i] = value;
                    return;
                }

                if (mGarbage && mSize >= mKeys.length) {
                    gc();

                    // Search again because indices may have changed.
                    i = ~binarySearch(mKeys, 0, mSize, key);
                }

                if (mSize >= mKeys.length) {
                    int n = ArrayUtils.idealIntArraySize(mSize + 1);

                    long[] nkeys = new long[n];
                    Object[] nvalues = new Object[n];

                    // Log.e("SparseArray", "grow " + mKeys.length + " to " +
                    // n);
                    System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
                    System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

                    mKeys = nkeys;
                    mValues = nvalues;
                }

                if (mSize - i != 0) {
                    // Log.e("SparseArray", "move " + (mSize - i));
                    System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
                    System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
                }

                mKeys[i] = key;
                mValues[i] = value;
                mSize++;
            }
        }

        /**
         * Returns the number of key-value mappings that this SparseArray currently stores.
         */
        public int size() {
            if (mGarbage) {
                gc();
            }

            return mSize;
        }

        /**
         * Given an index in the range <code>0...size()-1</code>, returns the key from the
         * <code>index</code>th key-value mapping that this SparseArray stores.
         */
        public long keyAt(int index) {
            if (mGarbage) {
                gc();
            }

            return mKeys[index];
        }

        /**
         * Given an index in the range <code>0...size()-1</code>, returns the value from the
         * <code>index</code>th key-value mapping that this SparseArray stores.
         */
        public E valueAt(int index) {
            if (mGarbage) {
                gc();
            }

            return (E) mValues[index];
        }

        /**
         * Given an index in the range <code>0...size()-1</code>, sets a new value for the
         * <code>index</code>th key-value mapping that this SparseArray stores.
         */
        public void setValueAt(int index, E value) {
            if (mGarbage) {
                gc();
            }

            mValues[index] = value;
        }

        /**
         * Returns the index for which {@link #keyAt} would return the specified key, or a negative
         * number if the specified key is not mapped.
         */
        public int indexOfKey(long key) {
            if (mGarbage) {
                gc();
            }

            return binarySearch(mKeys, 0, mSize, key);
        }

        /**
         * Returns an index for which {@link #valueAt} would return the specified key, or a negative
         * number if no keys map to the specified value. Beware that this is a linear search, unlike
         * lookups by key, and that multiple keys can map to the same value and this will find only
         * one of them.
         */
        public int indexOfValue(E value) {
            if (mGarbage) {
                gc();
            }

            for (int i = 0; i < mSize; i++)
                if (mValues[i] == value)
                    return i;

            return -1;
        }

        /**
         * Removes all key-value mappings from this SparseArray.
         */
        public void clear() {
            int n = mSize;
            Object[] values = mValues;

            for (int i = 0; i < n; i++) {
                values[i] = null;
            }

            mSize = 0;
            mGarbage = false;
        }

        /**
         * Puts a key/value pair into the array, optimizing for the case where the key is greater
         * than all existing keys in the array.
         */
        public void append(long key, E value) {
            if (mSize != 0 && key <= mKeys[mSize - 1]) {
                put(key, value);
                return;
            }

            if (mGarbage && mSize >= mKeys.length) {
                gc();
            }

            int pos = mSize;
            if (pos >= mKeys.length) {
                int n = ArrayUtils.idealIntArraySize(pos + 1);

                long[] nkeys = new long[n];
                Object[] nvalues = new Object[n];

                // Log.e("SparseArray", "grow " + mKeys.length + " to " + n);
                System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
                System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

                mKeys = nkeys;
                mValues = nvalues;
            }

            mKeys[pos] = key;
            mValues[pos] = value;
            mSize = pos + 1;
        }

        private static int binarySearch(long[] a, int start, int len, long key) {
            int high = start + len, low = start - 1, guess;

            while (high - low > 1) {
                guess = (high + low) / 2;

                if (a[guess] < key)
                    low = guess;
                else
                    high = guess;
            }

            if (high == start + len)
                return ~(start + len);
            else if (a[high] == key)
                return high;
            else
                return ~high;
        }

        private void checkIntegrity() {
            for (int i = 1; i < mSize; i++) {
                if (mKeys[i] <= mKeys[i - 1]) {
                    for (int j = 0; j < mSize; j++) {
                        Log.e("FAIL", j + ": " + mKeys[j] + " -> " + mValues[j]);
                    }

                    throw new RuntimeException();
                }
            }
        }

        private long[] mKeys;
        private Object[] mValues;
        private int mSize;
    }

    // XXX these should be changed to reflect the actual memory allocator we
    // use.
    // it looks like right now objects want to be powers of 2 minus 8
    // and the array size eats another 4 bytes

    /**
     * ArrayUtils contains some methods that you can call to find out the most efficient increments
     * by which to grow arrays. *
     *
     * <pre>
     * Source : https://github.com/android/platform_frameworks_base/blob/master/core/java/com/android/internal/util/ArrayUtils.java
     * </pre>
     */
    private static class ArrayUtils {
        private static Object[] EMPTY = new Object[0];
        private static final int CACHE_SIZE = 73;
        private static Object[] sCache = new Object[CACHE_SIZE];

        private ArrayUtils() { /* cannot be instantiated */
        }

        public static int idealByteArraySize(int need) {
            for (int i = 4; i < 32; i++)
                if (need <= (1 << i) - 12)
                    return (1 << i) - 12;

            return need;
        }

        public static int idealBooleanArraySize(int need) {
            return idealByteArraySize(need);
        }

        public static int idealShortArraySize(int need) {
            return idealByteArraySize(need * 2) / 2;
        }

        public static int idealCharArraySize(int need) {
            return idealByteArraySize(need * 2) / 2;
        }

        public static int idealIntArraySize(int need) {
            return idealByteArraySize(need * 4) / 4;
        }

        public static int idealFloatArraySize(int need) {
            return idealByteArraySize(need * 4) / 4;
        }

        public static int idealObjectArraySize(int need) {
            return idealByteArraySize(need * 4) / 4;
        }

        public static int idealLongArraySize(int need) {
            return idealByteArraySize(need * 8) / 8;
        }

        /**
         * Checks if the beginnings of two byte arrays are equal.
         *
         * @param array1 the first byte array
         * @param array2 the second byte array
         * @param length the number of bytes to check
         * @return true if they're equal, false otherwise
         */
        public static boolean equals(byte[] array1, byte[] array2, int length) {
            if (array1 == array2) {
                return true;
            }
            if (array1 == null || array2 == null || array1.length < length
                    || array2.length < length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (array1[i] != array2[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns an empty array of the specified type. The intent is that it will return the same
         * empty array every time to avoid reallocation, although this is not guaranteed.
         */
        public static <T> T[] emptyArray(Class<T> kind) {
            if (kind == Object.class) {
                return (T[]) EMPTY;
            }

            int bucket = ((System.identityHashCode(kind) / 8) & 0x7FFFFFFF) % CACHE_SIZE;
            Object cache = sCache[bucket];

            if (cache == null || cache.getClass().getComponentType() != kind) {
                cache = Array.newInstance(kind, 0);
                sCache[bucket] = cache;

                // Log.e("cache", "new empty " + kind.getName() + " at " +
                // bucket);
            }

            return (T[]) cache;
        }

        /**
         * Checks that value is present as at least one of the elements of the array.
         *
         * @param array the array to check in
         * @param value the value to check for
         * @return true if the value is present in the array
         */
        public static <T> boolean contains(T[] array, T value) {
            for (T element : array) {
                if (element == null) {
                    if (value == null)
                        return true;
                } else {
                    if (value != null && element.equals(value))
                        return true;
                }
            }
            return false;
        }

        public static boolean contains(int[] array, int value) {
            for (int element : array) {
                if (element == value) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Class to save the state for the compatibility version of GridView
     */
    static class SavedState extends BaseSavedState {
        int checkedItemCount;
        SparseBooleanArray checkState;
        LongSparseArray<Integer> checkIdState;

        /**
         * Constructor called from {@link AbsListView#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            checkedItemCount = in.readInt();
            checkState = in.readSparseBooleanArray();
            final int N = in.readInt();
            if (N > 0) {
                checkIdState = new LongSparseArray<Integer>();
                for (int i = 0; i < N; i++) {
                    final long key = in.readLong();
                    final int value = in.readInt();
                    checkIdState.put(key, value);
                }
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(checkedItemCount);
            out.writeSparseBooleanArray(checkState);
            final int N = checkIdState != null ? checkIdState.size() : 0;
            out.writeInt(N);
            for (int i = 0; i < N; i++) {
                out.writeLong(checkIdState.keyAt(i));
                out.writeInt(checkIdState.valueAt(i));
            }
        }

        @Override
        public String toString() {
            return "AbsListView.SavedState{" + Integer.toHexString(System.identityHashCode(this))
                    + " checkState=" + checkState + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    public GridViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs);
    }

    public GridViewCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(attrs);
    }

    public GridViewCompat(Context context) {
        super(context);
    }

    /**
     * This api is not implemented yet but can be implemented if you want to set the multi-selection
     * from the xml file not from the code
     */
    private void initAttrs(AttributeSet attrs) {
        copyNumColumns = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "numColumns", -1);
    }

    /**
     * WARN Do not call the default api
     *
     * @see #setChoiceMode(int)
     * @return The current choice mode
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public int getChoiceMode() {
        if (!inCompatibleMode && gridView_getChoiceMode != null)
            return super.getChoiceMode();
        return mChoiceModeC;
    }

    /*
     * (non-Javadoc)
     * @see android.widget.GridView#setAdapter(android.widget.ListAdapter)
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        if (!inCompatibleMode) {
            super.setAdapter(adapter);
            return;
        }

        // Code copied from Android source
        super.setAdapter(adapter);
        if (adapter != null) {
            if (mChoiceModeC != ListView.CHOICE_MODE_NONE && getAdapter().hasStableIds()
                    && mCheckedIdStatesC == null) {
                mCheckedIdStatesC = new LongSparseArray<Integer>();
            }
        }

        if (mCheckStatesC != null) {
            mCheckStatesC.clear();
        }

        if (mCheckedIdStatesC != null) {
            mCheckedIdStatesC.clear();
        }

    }

    /**
     * WARN Do not call the default api
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public long[] getCheckedItemIds() {
        if (!inCompatibleMode && gridView_getCheckedItemIds != null) {
            return super.getCheckedItemIds();
        }

        // Code copied from Android source
        if (mChoiceModeC == ListView.CHOICE_MODE_NONE || mCheckedIdStatesC == null
                || getAdapter() == null) {
            return new long[0];
        }

        final LongSparseArray<Integer> idStates = mCheckedIdStatesC;
        final int count = idStates.size();
        final long[] ids = new long[count];

        for (int i = 0; i < count; i++) {
            ids[i] = idStates.keyAt(i);
        }

        return ids;
    }

    /**
     * WARN Do not call the default api
     *
     * @param position
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean isItemChecked(int position) {
        if (!inCompatibleMode && gridView_isItemChecked != null) {
            return super.isItemChecked(position);
        }

        // Code copied from Android source
        if (mChoiceModeC != ListView.CHOICE_MODE_NONE && mCheckStatesC != null) {
            return mCheckStatesC.get(position);
        }

        return false;
    }

    /**
     * WARN Do not call the default api
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public int getCheckedItemPosition() {
        if (!inCompatibleMode && gridView_getCheckedItemPosition != null) {
            return super.getCheckedItemPosition();
        }

        // Code copied from Android source
        if (mChoiceModeC == ListView.CHOICE_MODE_SINGLE && mCheckStatesC != null
                && mCheckStatesC.size() == 1) {
            return mCheckStatesC.keyAt(0);
        }

        return INVALID_POSITION;
    }

    /**
     * WARN Do not call the default api
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SparseBooleanArray getCheckedItemPositions() {
        if (!inCompatibleMode) {
            return super.getCheckedItemPositions();
        }

        // Code copied from Android source
        if (mChoiceModeC != ListView.CHOICE_MODE_NONE) {
            return mCheckStatesC;
        }
        return null;
    }

    /**
     * WARN Do not call the default api
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void clearChoices() {
        if (!inCompatibleMode) {
            super.clearChoices();
            return;
        }

        // Code copied from Android source
        if (mCheckStatesC != null) {
            mCheckStatesC.clear();
        }
        if (mCheckedIdStatesC != null) {
            mCheckedIdStatesC.clear();
        }
        mCheckedItemCountC = 0;
    }

    /**
     * WARN Do not call the default api
     *
     * <pre>
     *
     * public void setItemChecked(int position, boolean value) {
     *     if (mChoiceMode == CHOICE_MODE_NONE) {
     *         return;
     *     }
     *
     *     // Start selection mode if needed. We don't need to if we're unchecking
     *     // something.
     *     if (value &amp;&amp; mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL &amp;&amp; mChoiceActionMode == null) {
     *         mChoiceActionMode = startActionMode(mMultiChoiceModeCallback);
     *     }
     *
     *     if (mChoiceMode == CHOICE_MODE_MULTIPLE || mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
     *         boolean oldValue = mCheckStates.get(position);
     *         mCheckStates.put(position, value);
     *         if (mCheckedIdStates != null &amp;&amp; mAdapter.hasStableIds()) {
     *             if (value) {
     *                 mCheckedIdStates.put(mAdapter.getItemId(position), position);
     *             } else {
     *                 mCheckedIdStates.delete(mAdapter.getItemId(position));
     *             }
     *         }
     *         if (oldValue != value) {
     *             if (value) {
     *                 mCheckedItemCount++;
     *             } else {
     *                 mCheckedItemCount--;
     *             }
     *         }
     *         if (mChoiceActionMode != null) {
     *             final long id = mAdapter.getItemId(position);
     *             mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode, position, id,
     *                 value);
     *         }
     *     } else {
     *         boolean updateIds = mCheckedIdStates != null &amp;&amp; mAdapter.hasStableIds();
     *         // Clear all values if we're checking something, or unchecking the
     *         // currently
     *         // selected item
     *         if (value || isItemChecked(position)) {
     *             mCheckStates.clear();
     *             if (updateIds) {
     *                 mCheckedIdStates.clear();
     *             }
     *         }
     *         // this may end up selecting the value we just cleared but this way
     *         // we ensure length of mCheckStates is 1, a fact getCheckedItemPosition
     *         // relies on
     *         if (value) {
     *             mCheckStates.put(position, true);
     *             if (updateIds) {
     *                 mCheckedIdStates.put(mAdapter.getItemId(position), position);
     *             }
     *             mCheckedItemCount = 1;
     *         } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
     *             mCheckedItemCount = 0;
     *         }
     *     }
     *
     *     // Do not generate a data change while we are in the layout phase
     *     if (!mInLayout &amp;&amp; !mBlockLayoutRequests) {
     *         mDataChanged = true;
     *         rememberSyncState();
     *         requestLayout();
     *     }
     * }
     *
     * We are using it where we dont have access to private members and we need to update views
     * public void invalidateViews() {
     *     mDataChanged = true;
     *     rememberSyncState();
     *     requestLayout();
     *     invalidate();
     * }
     * </pre>
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setItemChecked(int position, boolean value) {
        if (!inCompatibleMode) {
            super.setItemChecked(position, value);
            return;
        }

        // Code copied from Android source. The code below is slightly
        // different.
        if (mChoiceModeC == ListView.CHOICE_MODE_NONE) {
            return;
        }

        if (mChoiceModeC == ListView.CHOICE_MODE_MULTIPLE) {
            boolean oldValue = mCheckStatesC.get(position);
            mCheckStatesC.put(position, value);
            if (mCheckedIdStatesC != null && getAdapter().hasStableIds()) {
                if (value) {
                    mCheckedIdStatesC.put(getAdapter().getItemId(position), position);
                } else {
                    mCheckedIdStatesC.delete(getAdapter().getItemId(position));
                }
            }
            if (oldValue != value) {
                if (value) {
                    mCheckedItemCountC++;
                } else {
                    mCheckedItemCountC--;
                }
            }
        } else {
            boolean updateIds = mCheckedIdStatesC != null && getAdapter().hasStableIds();
            // Clear all values if we're checking something, or unchecking the
            // currently
            // selected item
            if (value || isItemChecked(position)) {
                mCheckStatesC.clear();
                if (updateIds) {
                    mCheckedIdStatesC.clear();
                }
            }
            // this may end up selecting the value we just cleared but this way
            // we ensure length of mCheckStates is 1, a fact
            // getCheckedItemPosition relies on
            if (value) {
                mCheckStatesC.put(position, true);
                if (updateIds) {
                    mCheckedIdStatesC.put(getAdapter().getItemId(position), position);
                }
                mCheckedItemCountC = 1;
            } else if (mCheckStatesC.size() == 0 || !mCheckStatesC.valueAt(0)) {
                mCheckedItemCountC = 0;
            }
        }

        // Since we dont have access to private members this is the closest we
        // can get.
        invalidateViews();
    }

    /**
     * <pre>
     *  public boolean performItemClick(View view, int position, long id) {
     *      boolean handled = false;
     *      boolean dispatchItemClick = true;
     *
     *      if (mChoiceMode != CHOICE_MODE_NONE) {
     *          handled = true;
     *
     *          if (mChoiceMode == CHOICE_MODE_MULTIPLE
     *              || (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL &amp;&amp; mChoiceActionMode != null)) {
     *              boolean newValue = !mCheckStates.get(position, false);
     *              mCheckStates.put(position, newValue);
     *              if (mCheckedIdStates != null &amp;&amp; mAdapter.hasStableIds()) {
     *                  if (newValue) {
     *                      mCheckedIdStates.put(mAdapter.getItemId(position), position);
     *                  } else {
     *                      mCheckedIdStates.delete(mAdapter.getItemId(position));
     *                  }
     *              }
     *              if (newValue) {
     *                  mCheckedItemCount++;
     *              } else {
     *                  mCheckedItemCount--;
     *              }
     *              if (mChoiceActionMode != null) {
     *                  mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode, position, id,
     *                      newValue);
     *                  dispatchItemClick = false;
     *              }
     *          } else if (mChoiceMode == CHOICE_MODE_SINGLE) {
     *              boolean newValue = !mCheckStates.get(position, false);
     *              if (newValue) {
     *                  mCheckStates.clear();
     *                  mCheckStates.put(position, true);
     *                  if (mCheckedIdStates != null &amp;&amp; mAdapter.hasStableIds()) {
     *                      mCheckedIdStates.clear();
     *                      mCheckedIdStates.put(mAdapter.getItemId(position), position);
     *                  }
     *                  mCheckedItemCount = 1;
     *              } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
     *                  mCheckedItemCount = 0;
     *              }
     *          }
     *
     *          mDataChanged = true;
     *          rememberSyncState();
     *          requestLayout();
     *      }
     *
     *      if (dispatchItemClick) {
     *          handled |= super.performItemClick(view, position, id);
     *      }
     *
     *      return handled;
     *  }
     *
     * We are using it where we dont have access to private members and we need to update views
     *  public void invalidateViews() {
     *      mDataChanged = true;
     *      rememberSyncState();
     *      requestLayout();
     *      invalidate();
     *  }
     *
     * </pre>
     */
    @Override
    public boolean performItemClick(View view, int position, long id) {
        if (!inCompatibleMode)
            return super.performItemClick(view, position, id);

        boolean handled = false;
        boolean dispatchItemClick = true;

        if (mChoiceModeC != ListView.CHOICE_MODE_NONE) {
            handled = true;

            if (mChoiceModeC == ListView.CHOICE_MODE_MULTIPLE) {
                boolean newValue = !mCheckStatesC.get(position, false);
                mCheckStatesC.put(position, newValue);
                if (mCheckedIdStatesC != null && getAdapter().hasStableIds()) {
                    if (newValue) {
                        mCheckedIdStatesC.put(getAdapter().getItemId(position), position);
                    } else {
                        mCheckedIdStatesC.delete(getAdapter().getItemId(position));
                    }
                }
                if (newValue) {
                    mCheckedItemCountC++;
                } else {
                    mCheckedItemCountC--;
                }
            } else if (mChoiceModeC == ListView.CHOICE_MODE_SINGLE) {
                boolean newValue = !mCheckStatesC.get(position, false);
                if (newValue) {
                    mCheckStatesC.clear();
                    mCheckStatesC.put(position, true);
                    if (mCheckedIdStatesC != null && getAdapter().hasStableIds()) {
                        mCheckedIdStatesC.clear();
                        mCheckedIdStatesC.put(getAdapter().getItemId(position), position);
                    }
                    mCheckedItemCountC = 1;
                } else if (mCheckStatesC.size() == 0 || !mCheckStatesC.valueAt(0)) {
                    mCheckedItemCountC = 0;
                }
            }

            // Since we dont have access to private members this is the closest
            // we can get.
            invalidateViews();
        }

        if (dispatchItemClick) {
            handled |= super.performItemClick(view, position, id);
        }

        return handled;
    }

    /**
     * WARN Do not call the default api
     *
     * @param choiceMode
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setChoiceMode(int choiceMode) {
        if (!inCompatibleMode) {
            super.setChoiceMode(choiceMode);
            return;
        }

        // Code copied from Android source
        mChoiceModeC = choiceMode;
        if (mChoiceModeC != ListView.CHOICE_MODE_NONE) {
            if (mCheckStatesC == null) {
                mCheckStatesC = new SparseBooleanArray();
            }
            if (mCheckedIdStatesC == null && getAdapter() != null && getAdapter().hasStableIds()) {
                mCheckedIdStatesC = new LongSparseArray<Integer>();
            }
        }
    }

    private SparseBooleanArray makeClone(SparseBooleanArray sba) {
        // Code copied from Android source
        SparseBooleanArray sbaClone = new SparseBooleanArray();
        int sbaLen = sba.size();
        for (int i = 0; i < sbaLen; i++) {
            int key = sba.keyAt(i);
            sbaClone.put(key, sba.get(key));
        }
        return sbaClone;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (!inCompatibleMode) {
            return super.onSaveInstanceState();
        }

        // Restoring the state if we are in compatible mode
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        if (mCheckStatesC != null) {
            ss.checkState = makeClone(mCheckStatesC);
        }

        if (mCheckedIdStatesC != null) {
            final LongSparseArray<Integer> idState = new LongSparseArray<Integer>();
            final int count = mCheckedIdStatesC.size();
            for (int i = 0; i < count; i++) {
                idState.put(mCheckedIdStatesC.keyAt(i), mCheckedIdStatesC.valueAt(i));
            }
            ss.checkIdState = idState;
        }
        ss.checkedItemCount = mCheckedItemCountC;

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!inCompatibleMode) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;

        super.onRestoreInstanceState(ss.getSuperState());

        if (ss.checkState != null) {
            mCheckStatesC = ss.checkState;
        }

        if (ss.checkIdState != null) {
            mCheckedIdStatesC = ss.checkIdState;
        }

        mCheckedItemCountC = ss.checkedItemCount;

        // Since we dont have access to private members this is the closest we
        // can get.
        invalidateViews();
    }

    /**
     * WARN Do not call the default api
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public int getCheckedItemCount() {
        if (!inCompatibleMode) {
            return super.getCheckedItemCount();
        }

        return mCheckedItemCountC;
    }

    /**
     * {@inheritDoc}
     */
    public void setNumColumns(int numColumns) {
        if (inCompatibleMode) {
            copyNumColumns = numColumns;
        }
        super.setNumColumns(numColumns);
    }

    /**
     * {@inheritDoc}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public int getNumColumns() {
        if (!inCompatibleMode) {
            return super.getNumColumns();
        }
        return copyNumColumns;
    }
}
