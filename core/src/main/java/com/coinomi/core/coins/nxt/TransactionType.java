package com.coinomi.core.coins.nxt;


//import org.json.simple.JSONObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public abstract class TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte TYPE_MESSAGING = 1;
    private static final byte TYPE_COLORED_COINS = 2;
    private static final byte TYPE_DIGITAL_GOODS = 3;
    private static final byte TYPE_ACCOUNT_CONTROL = 4;
    
    private static final byte TYPE_BURST_MINING = 20; // jump some for easier nxt updating
    private static final byte TYPE_ADVANCED_PAYMENT = 21;
    private static final byte TYPE_AUTOMATED_TRANSACTIONS = 22;

    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    private static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    private static final byte SUBTYPE_MESSAGING_POLL_CREATION = 2;
    private static final byte SUBTYPE_MESSAGING_VOTE_CASTING = 3;
    private static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 4;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
    private static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
    private static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;

    private static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
    private static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
    private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
    private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
    private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
    private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;

    private static final byte SUBTYPE_DIGITAL_GOODS_LISTING = 0;
    private static final byte SUBTYPE_DIGITAL_GOODS_DELISTING = 1;
    private static final byte SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE = 2;
    private static final byte SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE = 3;
    private static final byte SUBTYPE_DIGITAL_GOODS_PURCHASE = 4;
    private static final byte SUBTYPE_DIGITAL_GOODS_DELIVERY = 5;
    private static final byte SUBTYPE_DIGITAL_GOODS_FEEDBACK = 6;
    private static final byte SUBTYPE_DIGITAL_GOODS_REFUND = 7;
    
    private static final byte SUBTYPE_AT_CREATION = 0;
    private static final byte SUBTYPE_AT_NXT_PAYMENT = 1;

    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    
    private static final byte SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT = 0;
    
    private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION = 0;
    private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN = 1;
    private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT = 2;
    private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE = 3;
    private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL = 4;
    private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT = 5;

    private static final int BASELINE_FEE_HEIGHT = 1; // At release time must be less than current block - 1440
    private static final Fee BASELINE_FEE = new Fee(Constants.ONE_NXT, 0);
    private static final Fee BASELINE_ASSET_ISSUANCE_FEE = new Fee(1000 * Constants.ONE_NXT, 0);
    private static final int NEXT_FEE_HEIGHT = Integer.MAX_VALUE;
    private static final Fee NEXT_FEE = new Fee(Constants.ONE_NXT, 0);
    private static final Fee NEXT_ASSET_ISSUANCE_FEE = new Fee(1000 * Constants.ONE_NXT, 0);

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return Messaging.ARBITRARY_MESSAGE;
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                        return Messaging.ALIAS_ASSIGNMENT;
                    case SUBTYPE_MESSAGING_POLL_CREATION:
                        return Messaging.POLL_CREATION;
                    case SUBTYPE_MESSAGING_VOTE_CASTING:
                        return Messaging.VOTE_CASTING;
                    case SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        return Messaging.HUB_ANNOUNCEMENT;
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                    case SUBTYPE_MESSAGING_ALIAS_SELL:
                        return Messaging.ALIAS_SELL;
                    case SUBTYPE_MESSAGING_ALIAS_BUY:
                        return Messaging.ALIAS_BUY;
                    default:
                        return null;
                }
            case TYPE_COLORED_COINS:
                switch (subtype) {
                    case SUBTYPE_COLORED_COINS_ASSET_ISSUANCE:
                        return ColoredCoins.ASSET_ISSUANCE;
                    case SUBTYPE_COLORED_COINS_ASSET_TRANSFER:
                        return ColoredCoins.ASSET_TRANSFER;
                    case SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT:
                        return ColoredCoins.ASK_ORDER_PLACEMENT;
                    case SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT:
                        return ColoredCoins.BID_ORDER_PLACEMENT;
                    case SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION:
                        return ColoredCoins.ASK_ORDER_CANCELLATION;
                    case SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION:
                        return ColoredCoins.BID_ORDER_CANCELLATION;
                    default:
                        return null;
                }
            case TYPE_DIGITAL_GOODS:
                switch (subtype) {
                    case SUBTYPE_DIGITAL_GOODS_LISTING:
                        return DigitalGoods.LISTING;
                    case SUBTYPE_DIGITAL_GOODS_DELISTING:
                        return DigitalGoods.DELISTING;
                    case SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE:
                        return DigitalGoods.PRICE_CHANGE;
                    case SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE:
                        return DigitalGoods.QUANTITY_CHANGE;
                    case SUBTYPE_DIGITAL_GOODS_PURCHASE:
                        return DigitalGoods.PURCHASE;
                    case SUBTYPE_DIGITAL_GOODS_DELIVERY:
                        return DigitalGoods.DELIVERY;
                    case SUBTYPE_DIGITAL_GOODS_FEEDBACK:
                        return DigitalGoods.FEEDBACK;
                    case SUBTYPE_DIGITAL_GOODS_REFUND:
                        return DigitalGoods.REFUND;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return AccountControl.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
                /*case TYPE_BURST_MINING:
                switch (subtype) {
                    case SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT:
                        return BurstMining.REWARD_RECIPIENT_ASSIGNMENT;
                    default:
                        return null;
                }
            case TYPE_ADVANCED_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION:
                        return AdvancedPayment.ESCROW_CREATION;
                    case SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN:
                        return AdvancedPayment.ESCROW_SIGN;
                    case SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT:
                        return AdvancedPayment.ESCROW_RESULT;
                    case SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE:
                        return AdvancedPayment.SUBSCRIPTION_SUBSCRIBE;
                    case SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL:
                        return AdvancedPayment.SUBSCRIPTION_CANCEL;
                    case SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT:
                        return AdvancedPayment.SUBSCRIPTION_PAYMENT;
                    default:
                        return null;
                }
            case TYPE_AUTOMATED_TRANSACTIONS:
                switch (subtype) {
                    case SUBTYPE_AT_CREATION:
                        return AutomatedTransactions.AUTOMATED_TRANSACTION_CREATION;
                    case SUBTYPE_AT_NXT_PAYMENT:
                        return AutomatedTransactions.AT_PAYMENT;
                    default:
                        return null;
                }*/
            default:
                return null;
        }
    }

    private TransactionType() {
    }

    public abstract byte getType();

    public abstract byte getSubtype();

    abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException;

    abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws JSONException, NxtException.NotValidException;

    //abstract void validateAttachment(Transaction transaction) throws NxtException.ValidationException;

    // return false iff double spending
    

    public abstract boolean hasRecipient();
    
    public boolean isSigned() {
        return true;
    }

    @Override
    public final String toString() {
        return "type: " + getType() + ", subtype: " + getSubtype();
    }

    /*
    Collection<TransactionType> getPhasingTransactionTypes() {
        return Collections.emptyList();
    }

    Collection<TransactionType> getPhasedTransactionTypes() {
        return Collections.emptyList();
    }
    */

    public static abstract class Payment extends TransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_PAYMENT;
        }

        @Override
        final public boolean hasRecipient() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws JSONException {
                return Attachment.ORDINARY_PAYMENT;
            }


        };

    }

    public static abstract class Messaging extends TransactionType {

        private Messaging() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_MESSAGING;
        }

     

        public final static TransactionType ARBITRARY_MESSAGE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws JSONException {
                return Attachment.ARBITRARY_MESSAGE;
            }


            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public static final TransactionType ALIAS_ASSIGNMENT = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAliasAssignment(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(JSONObject attachmentData) throws JSONException {
                return new Attachment.MessagingAliasAssignment(attachmentData);
            }


            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType ALIAS_SELL = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_SELL;
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAliasSell(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(JSONObject attachmentData) throws JSONException {
                return new Attachment.MessagingAliasSell(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public static final TransactionType ALIAS_BUY = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_BUY;
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAliasBuy(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(JSONObject attachmentData) throws JSONException {
                return new Attachment.MessagingAliasBuy(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public final static TransactionType POLL_CREATION = new Messaging() {
            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_POLL_CREATION;
            }

            @Override
            Attachment.MessagingPollCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingPollCreation(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingPollCreation parseAttachment(JSONObject attachmentData) throws JSONException {
                return new Attachment.MessagingPollCreation(attachmentData);
            }
            
            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public final static TransactionType VOTE_CASTING = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_VOTE_CASTING;
            }

            @Override
            Attachment.MessagingVoteCasting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingVoteCasting(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingVoteCasting parseAttachment(JSONObject attachmentData) throws JSONException {
                return new Attachment.MessagingVoteCasting(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType HUB_ANNOUNCEMENT = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT;
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.MessagingHubAnnouncement(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final Messaging ACCOUNT_INFO = new Messaging() {

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.MessagingAccountInfo(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

    }

    public static abstract class ColoredCoins extends TransactionType {

        private ColoredCoins() {}

        @Override
        public final byte getType() {
            return TransactionType.TYPE_COLORED_COINS;
        }

        public static final TransactionType ASSET_ISSUANCE = new ColoredCoins() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE;
            }

            @Override
            public Fee getBaselineFee() {
                return BASELINE_ASSET_ISSUANCE_FEE;
            }

            @Override
            public Fee getNextFee() {
                return NEXT_ASSET_ISSUANCE_FEE;
            }

            @Override
            Attachment.ColoredCoinsAssetIssuance parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsAssetIssuance(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsAssetIssuance parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsAssetIssuance(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType ASSET_TRANSFER = new ColoredCoins() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASSET_TRANSFER;
            }

            @Override
            Attachment.ColoredCoinsAssetTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsAssetTransfer(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsAssetTransfer parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsAssetTransfer(attachmentData);
            }

         

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        abstract static class ColoredCoinsOrderPlacement extends ColoredCoins {

           

            @Override
            final public boolean hasRecipient() {
                return false;
            }

        }

        public static final TransactionType ASK_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT;
            }

            @Override
            Attachment.ColoredCoinsAskOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderPlacement(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsAskOrderPlacement parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsAskOrderPlacement(attachmentData);
            }

            

        };

        public final static TransactionType BID_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT;
            }

            @Override
            Attachment.ColoredCoinsBidOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderPlacement(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsBidOrderPlacement parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsBidOrderPlacement(attachmentData);
            }

           

        };

        abstract static class ColoredCoinsOrderCancellation extends ColoredCoins {

            @Override
            public boolean hasRecipient() {
                return false;
            }

        }

        public static final TransactionType ASK_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION;
            }

            @Override
            Attachment.ColoredCoinsAskOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderCancellation(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsAskOrderCancellation parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsAskOrderCancellation(attachmentData);
            }
        };

        public static final TransactionType BID_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION;
            }

            @Override
            Attachment.ColoredCoinsBidOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderCancellation(buffer, transactionVersion);
            }

            @Override
            Attachment.ColoredCoinsBidOrderCancellation parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.ColoredCoinsBidOrderCancellation(attachmentData);
            }

       
        };
    }

    public static abstract class DigitalGoods extends TransactionType {

        private DigitalGoods() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_DIGITAL_GOODS;
        }

       


        public static final TransactionType LISTING = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_LISTING;
            }

            @Override
            Attachment.DigitalGoodsListing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsListing(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsListing parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsListing(attachmentData);
            }


            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType DELISTING = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_DELISTING;
            }

            @Override
            Attachment.DigitalGoodsDelisting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsDelisting(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsDelisting parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsDelisting(attachmentData);
            }

         

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType PRICE_CHANGE = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE;
            }

            @Override
            Attachment.DigitalGoodsPriceChange parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsPriceChange(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsPriceChange parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsPriceChange(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType QUANTITY_CHANGE = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE;
            }

            @Override
            Attachment.DigitalGoodsQuantityChange parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsQuantityChange(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsQuantityChange parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsQuantityChange(attachmentData);
            }

        

            @Override
            public boolean hasRecipient() {
                return false;
            }

        };

        public static final TransactionType PURCHASE = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_PURCHASE;
            }

            @Override
            Attachment.DigitalGoodsPurchase parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsPurchase(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsPurchase parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsPurchase(attachmentData);
            }


            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public static final TransactionType DELIVERY = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_DELIVERY;
            }

            @Override
            Attachment.DigitalGoodsDelivery parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsDelivery(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsDelivery parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsDelivery(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public static final TransactionType FEEDBACK = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_FEEDBACK;
            }

            @Override
            Attachment.DigitalGoodsFeedback parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsFeedback(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsFeedback parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsFeedback(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

        public static final TransactionType REFUND = new DigitalGoods() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_DIGITAL_GOODS_REFUND;
            }

            @Override
            Attachment.DigitalGoodsRefund parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.DigitalGoodsRefund(buffer, transactionVersion);
            }

            @Override
            Attachment.DigitalGoodsRefund parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
                return new Attachment.DigitalGoodsRefund(attachmentData);
            }

            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

    }

    public static abstract class AccountControl extends TransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ACCOUNT_CONTROL;
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }


            @Override
            public boolean hasRecipient() {
                return true;
            }

        };

    }
    
    /*public static abstract class AdvancedPayment extends TransactionType {

        private AdvancedPayment() {}

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ADVANCED_PAYMENT;
        }

        public final static TransactionType ESCROW_CREATION = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION;
            }

            @Override
            Attachment.AdvancedPaymentEscrowCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowCreation(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentEscrowCreation parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowCreation(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
                Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNQT(), attachment.getTotalSigners() * Constants.ONE_NXT);
                if(senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT.longValue()) {
                    return false;
                }
                senderAccount.addToUnconfirmedBalanceNQT(-totalAmountNQT);
                return true;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
                Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNQT(), attachment.getTotalSigners() * Constants.ONE_NXT);
                senderAccount.addToBalanceNQT(-totalAmountNQT);
                Collection<Long> signers = attachment.getSigners();
                for(Long signer : signers) {
                    Account.addOrGetAccount(signer).addToBalanceAndUnconfirmedBalanceNQT(Constants.ONE_NXT);
                }
                Escrow.addEscrowTransaction(senderAccount,
                                            recipientAccount,
                                            transaction.getId(),
                                            attachment.getAmountNQT(),
                                            attachment.getRequiredSigners(),
                                            attachment.getSigners(),
                                            transaction.getTimestamp() + attachment.getDeadline(),
                                            attachment.getDeadlineAction());
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
                Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNQT(), attachment.getTotalSigners() * Constants.ONE_NXT);
                senderAccount.addToUnconfirmedBalanceNQT(totalAmountNQT);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                return false;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
                Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNQT(), transaction.getFeeNQT());
                if(transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Escrow must have different sender and recipient");
                }
                totalAmountNQT = Convert.safeAdd(totalAmountNQT, attachment.getTotalSigners() * Constants.ONE_NXT);
                if(transaction.getAmountNQT() != 0) {
                    throw new NxtException.NotValidException("Transaction sent amount must be 0 for escrow");
                }
                if(totalAmountNQT.compareTo(0L) < 0 ||
                   totalAmountNQT.compareTo(Constants.MAX_BALANCE_NQT) > 0)
                {
                    throw new NxtException.NotValidException("Invalid escrow creation amount");
                }
                if(transaction.getFeeNQT() < Constants.ONE_NXT) {
                    throw new NxtException.NotValidException("Escrow transaction must have a fee at least 1 burst");
                }
                if(attachment.getRequiredSigners() < 1 || attachment.getRequiredSigners() > 10) {
                    throw new NxtException.NotValidException("Escrow required signers much be 1 - 10");
                }
                if(attachment.getRequiredSigners() > attachment.getTotalSigners()) {
                    throw new NxtException.NotValidException("Cannot have more required than signers on escrow");
                }
                if(attachment.getTotalSigners() < 1 || attachment.getTotalSigners() > 10) {
                    throw new NxtException.NotValidException("Escrow transaction requires 1 - 10 signers");
                }
                if(attachment.getDeadline() < 1 || attachment.getDeadline() > 7776000) { // max deadline 3 months
                    throw new NxtException.NotValidException("Escrow deadline must be 1 - 7776000 seconds");
                }
                if(attachment.getDeadlineAction() == null || attachment.getDeadlineAction() == Escrow.DecisionType.UNDECIDED) {
                    throw new NxtException.NotValidException("Invalid deadline action for escrow");
                }
                if(attachment.getSigners().contains(transaction.getSenderId()) ||
                   attachment.getSigners().contains(transaction.getRecipientId())) {
                    throw new NxtException.NotValidException("Escrow sender and recipient cannot be signers");
                }
                if(!Escrow.isEnabled()) {
                    throw new NxtException.NotYetEnabledException("Escrow not yet enabled");
                }
            }

            @Override
            final public boolean hasRecipient() {
                return true;
            }
        };

        public final static TransactionType ESCROW_SIGN = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN;
            }

            @Override
            Attachment.AdvancedPaymentEscrowSign parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowSign(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentEscrowSign parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowSign(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return true;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
                Escrow escrow = Escrow.getEscrowTransaction(attachment.getEscrowId());
                escrow.sign(senderAccount.getId(), attachment.getDecision());
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
                String uniqueString = Convert.toUnsignedLong(attachment.getEscrowId()) + ":" +
                                      Convert.toUnsignedLong(transaction.getSenderId());
                return isDuplicate(AdvancedPayment.ESCROW_SIGN, uniqueString, duplicates);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
                if(transaction.getAmountNQT() != 0 || transaction.getFeeNQT() != Constants.ONE_NXT) {
                    throw new NxtException.NotValidException("Escrow signing must have amount 0 and fee of 1");
                }
                if(attachment.getEscrowId() == null || attachment.getDecision() == null) {
                    throw new NxtException.NotValidException("Escrow signing requires escrow id and decision set");
                }
                Escrow escrow = Escrow.getEscrowTransaction(attachment.getEscrowId());
                if(escrow == null) {
                    throw new NxtException.NotValidException("Escrow transaction not found");
                }
                if(!escrow.isIdSigner(transaction.getSenderId()) &&
                   !escrow.getSenderId().equals(transaction.getSenderId()) &&
                   !escrow.getRecipientId().equals(transaction.getSenderId())) {
                    throw new NxtException.NotValidException("Sender is not a participant in specified escrow");
                }
                if(escrow.getSenderId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.RELEASE) {
                    throw new NxtException.NotValidException("Escrow sender can only release");
                }
                if(escrow.getRecipientId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.REFUND) {
                    throw new NxtException.NotValidException("Escrow recipient can only refund");
                }
                if(!Escrow.isEnabled()) {
                    throw new NxtException.NotYetEnabledException("Escrow not yet enabled");
                }
            }

            @Override
            final public boolean hasRecipient() {
                return false;
            }
        };

        public final static TransactionType ESCROW_RESULT = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT;
            }

            @Override
            Attachment.AdvancedPaymentEscrowResult parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowResult(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentEscrowResult parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentEscrowResult(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return false;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                return true;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                throw new NxtException.NotValidException("Escrow result never validates");
            }

            @Override
            final public boolean hasRecipient() {
                return true;
            }

            @Override
            final public boolean isSigned() {
                return false;
            }
        };

        public final static TransactionType SUBSCRIPTION_SUBSCRIBE = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE;
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionSubscribe parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionSubscribe(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionSubscribe parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionSubscribe(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return true;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
                Subscription.addSubscription(senderAccount, recipientAccount, transaction.getId(), transaction.getAmountNQT(), transaction.getTimestamp(), attachment.getFrequency());
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                return false;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
                if(attachment.getFrequency() == null ||
                   attachment.getFrequency().intValue() < Constants.BURST_SUBSCRIPTION_MIN_FREQ ||
                   attachment.getFrequency().intValue() > Constants.BURST_SUBSCRIPTION_MAX_FREQ) {
                    throw new NxtException.NotValidException("Invalid subscription frequency");
                }
                if(transaction.getAmountNQT() < Constants.ONE_NXT || transaction.getAmountNQT() > Constants.MAX_BALANCE_NQT) {
                    throw new NxtException.NotValidException("Subscriptions must be at least one burst");
                }
                if(transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Cannot create subscription to same address");
                }
                if(!Subscription.isEnabled()) {
                    throw new NxtException.NotYetEnabledException("Subscriptions not yet enabled");
                }
            }

            @Override
            final public boolean hasRecipient() {
                return true;
            }
        };

        public final static TransactionType SUBSCRIPTION_CANCEL = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL;
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionCancel parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionCancel(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionCancel parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionCancel(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
                Subscription.addRemoval(attachment.getSubscriptionId());
                return true;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
                Subscription.removeSubscription(attachment.getSubscriptionId());
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
                return isDuplicate(AdvancedPayment.SUBSCRIPTION_CANCEL, Convert.toUnsignedLong(attachment.getSubscriptionId()), duplicates);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
                if(attachment.getSubscriptionId() == null) {
                    throw new NxtException.NotValidException("Subscription cancel must include subscription id");
                }

                Subscription subscription = Subscription.getSubscription(attachment.getSubscriptionId());
                if(subscription == null) {
                    throw new NxtException.NotValidException("Subscription cancel must contain current subscription id");
                }

                if(!subscription.getSenderId().equals(transaction.getSenderId()) &&
                   !subscription.getRecipientId().equals(transaction.getSenderId())) {
                    throw new NxtException.NotValidException("Subscription cancel can only be done by participants");
                }

                if(!Subscription.isEnabled()) {
                    throw new NxtException.NotYetEnabledException("Subscription cancel not yet enabled");
                }
            }

            @Override
            final public boolean hasRecipient() {
                return false;
            }
        };

        public final static TransactionType SUBSCRIPTION_PAYMENT = new AdvancedPayment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT;
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionPayment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionPayment(buffer, transactionVersion);
            }

            @Override
            Attachment.AdvancedPaymentSubscriptionPayment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AdvancedPaymentSubscriptionPayment(attachmentData);
            }

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return false;
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Set<String>> duplicates) {
                return true;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                throw new NxtException.NotValidException("Subscription payment never validates");
            }

            @Override
            final public boolean hasRecipient() {
                return true;
            }

            @Override
            final public boolean isSigned() {
                return false;
            }
        };
    }*/

    long minimumFeeNQT(int height, int appendagesSize) {
        if (height < BASELINE_FEE_HEIGHT) {
            return 0; // No need to validate fees before baseline block
        }
        Fee fee;
        if (height >= NEXT_FEE_HEIGHT) {
            fee = getNextFee();
        } else {
            fee = getBaselineFee();
        }
        return Convert.safeAdd(fee.getConstantFee(), Convert.safeMultiply(appendagesSize, fee.getAppendagesFee()));
    }

    protected Fee getBaselineFee() {
        return BASELINE_FEE;
    }

    protected Fee getNextFee() {
        return NEXT_FEE;
    }

    public static final class Fee {
        private final long constantFee;
        private final long appendagesFee;

        public Fee(long constantFee, long appendagesFee) {
            this.constantFee = constantFee;
            this.appendagesFee = appendagesFee;
        }

        public long getConstantFee() {
            return constantFee;
        }

        public long getAppendagesFee() {
            return appendagesFee;
        }

        @Override
        public String toString() {
            return "Fee{" +
                    "constantFee=" + constantFee +
                    ", appendagesFee=" + appendagesFee +
                    '}';
        }
    }

}
