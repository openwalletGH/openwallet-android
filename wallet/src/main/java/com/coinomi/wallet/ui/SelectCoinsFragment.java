package com.coinomi.wallet.ui;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.HeaderWithFontIcon;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that restores a wallet
 */
public class SelectCoinsFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SelectCoinsFragment.class);
    private Listener mListener;
    private String message;
    private boolean isMultipleChoice;
    private HeaderGridView coinsGridView;
    private Button nextButton;

    public static Fragment newInstance(Bundle args) {
        SelectCoinsFragment fragment = new SelectCoinsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static Fragment newInstance(String message, boolean isMultipleChoice, Bundle args) {
        args = args != null ? args : new Bundle();
        args.putString(Constants.ARG_MESSAGE, message);
        args.putBoolean(Constants.ARG_MULTIPLE_CHOICE, isMultipleChoice);
        return newInstance(args);
    }

    public SelectCoinsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Bundle args = getArguments();
            isMultipleChoice = args.getBoolean(Constants.ARG_MULTIPLE_CHOICE);
            message = args.getString(Constants.ARG_MESSAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_add_coins, container, false);

        nextButton = (Button) view.findViewById(R.id.button_next);
        if (isMultipleChoice) {
            nextButton.setEnabled(false);
            nextButton.setOnClickListener(getNextOnClickListener());
        } else {
            nextButton.setVisibility(View.GONE);
        }

        coinsGridView = (HeaderGridView) view.findViewById(R.id.coins_grid);
        // Set header if needed
        if (message != null) {
            HeaderWithFontIcon header = new HeaderWithFontIcon(getActivity());
            header.setFontIcon(R.string.font_icon_coins);
            header.setMessage(R.string.select_coins);
            coinsGridView.addHeaderView(header, null, false);
        }
        if (isMultipleChoice) coinsGridView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        coinsGridView.setOnItemClickListener(getItemClickListener());
        coinsGridView.setAdapter(new CoinsListAdapter(getActivity(), Constants.SUPPORTED_COINS));

        return view;
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        update();
    }

    private View.OnClickListener getNextOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> ids = new ArrayList<String>();
                SparseBooleanArray selected = coinsGridView.getCheckedItemPositions();
                for (int i = 0; i < selected.size(); i++) {
                    if (selected.valueAt(i)) {
                        CoinType type = (CoinType) coinsGridView.getItemAtPosition(selected.keyAt(i));
                        ids.add(type.getId());
                    }
                }
                selectCoins(ids);
            }
        };
    }

    private AdapterView.OnItemClickListener getItemClickListener() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                update(position);
            }
        };
    }

    private void update() {
        update(-1);
    }

    private void update(int currentSelection) {
        if (isMultipleChoice) {
            boolean isCoinSelected = coinsGridView.getCheckedItemCount() > 0;
            nextButton.setEnabled(isCoinSelected);
        } else if (currentSelection >= 0) {
            CoinType type = (CoinType) coinsGridView.getItemAtPosition(currentSelection);
            selectCoins(Lists.newArrayList(type.getId()));
        }
    }

    private void selectCoins(ArrayList<String> ids) {
        if (mListener != null) {
            Bundle args = getArguments() == null ? new Bundle() : getArguments();
            args.putStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS, ids);
            mListener.onCoinSelection(args);
        }
    }

    private ArrayList<String> typesToIds(List<CoinType> types) {
        ArrayList<String> ids = new ArrayList<String>(types.size());
        for (CoinType type : types) {
            ids.add(type.getId());
        }
        return ids;
    }

    WalletApplication getWalletApplication() {
        return (WalletApplication) getActivity().getApplication();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface Listener {
        public void onCoinSelection(Bundle args);
    }
}