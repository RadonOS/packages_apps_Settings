/*
 * Copyright (C) 2020 The Android Open Source Project
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
 */
package com.android.settings.network.telephony;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;

/**
 * Preference controller for "Enable 2G"
 *
 * <p>
 * This preference controller is invoked per subscription id, which means toggling 2g is a per-sim
 * operation. The requested 2g preference is delegated to
 * {@link TelephonyManager#setAllowedNetworkTypesForReason(int reason, long allowedNetworkTypes)}
 * with:
 * <ul>
 *     <li>{@code reason} {@link TelephonyManager#ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G}.</li>
 *     <li>{@code allowedNetworkTypes} with set or cleared 2g-related bits, depending on the
 *     requested preference state. </li>
 * </ul>
 */
public class Enable2gPreferenceController extends TelephonyTogglePreferenceController {

    private static final String LOG_TAG = "Enable2gPreferenceController";
    private static final long BITMASK_2G =  TelephonyManager.NETWORK_TYPE_BITMASK_GSM
                | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
                | TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                | TelephonyManager.NETWORK_TYPE_BITMASK_CDMA
                | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;

    private final MetricsFeatureProvider mMetricsFeatureProvider;

    private CarrierConfigManager mCarrierConfigManager;
    private TelephonyManager mTelephonyManager;

    /**
     * Class constructor of "Enable 2G" toggle.
     *
     * @param context of settings
     * @param key assigned within UI entry of XML file
     */
    public Enable2gPreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mMetricsFeatureProvider = FeatureFactory.getFactory(context).getMetricsFeatureProvider();
    }

    /**
     * Initialization based on a given subscription id.
     *
     * @param subId is the subscription id
     * @return this instance after initialization
     */
    public Enable2gPreferenceController init(int subId) {
        mSubId = subId;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
              .createForSubscriptionId(mSubId);
        return this;
    }

    /**
     * Get the {@link com.android.settings.core.BasePreferenceController.AvailabilityStatus} for
     * this preference given a {@code subId}.
     * <p>
     * A return value of {@link #AVAILABLE} denotes that the 2g status can be updated for this
     * particular subscription.
     * We return {@link #AVAILABLE} if the following conditions are met and {@link
     * #CONDITIONALLY_UNAVAILABLE} otherwise.
     * <ul>
     *     <li>The subscription is usable {@link SubscriptionManager#isUsableSubscriptionId}</li>
     *     <li>The carrier has not opted to disable this preference
     *     {@link CarrierConfigManager#KEY_HIDE_ENABLE_2G}</li>
     *     <li>The device supports
     *     <a href="https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/radio/1.6/IRadio.hal">Radio HAL version 1.6 or greater</a> </li>
     * </ul>
     */
    @Override
    public int getAvailabilityStatus(int subId) {
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(subId);
        if (mTelephonyManager == null) {
            Log.w(LOG_TAG, "Telephony manager not yet initialized");
            mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        }
        boolean visible =
                SubscriptionManager.isUsableSubscriptionId(subId)
                && carrierConfig != null
                && !carrierConfig.getBoolean(CarrierConfigManager.KEY_HIDE_ENABLE_2G)
                && mTelephonyManager.isRadioInterfaceCapabilitySupported(
                    mTelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK);
        return visible ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    /**
     * Return {@code true} if 2g is currently enabled.
     *
     * <p><b>NOTE:</b> This method returns the active state of the preference controller and is not
     * the parameter passed into {@link #setChecked(boolean)}, which is instead the requested future
     * state.</p>
     */
    @Override
    public boolean isChecked() {
        long currentlyAllowedNetworkTypes = mTelephonyManager.getAllowedNetworkTypesForReason(
                mTelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G);
        return (currentlyAllowedNetworkTypes & BITMASK_2G) != 0;
    }

    /**
     * Ensure that the modem's allowed network types are configured according to the user's
     * preference.
     * <p>
     * See {@link com.android.settings.core.TogglePreferenceController#setChecked(boolean)} for
     * details.
     *
     * @param isChecked The toggle value that we're being requested to enforce. A value of {@code
     *                  false} denotes that 2g will be disabled by the modem after this function
     *                  completes, if it is not already.
     */
    @Override
    public boolean setChecked(boolean isChecked) {
        if (!SubscriptionManager.isUsableSubscriptionId(mSubId)) {
            return false;
        }
        long currentlyAllowedNetworkTypes = mTelephonyManager.getAllowedNetworkTypesForReason(
                mTelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G);
        boolean enabled = (currentlyAllowedNetworkTypes & BITMASK_2G) != 0;
        if (enabled == isChecked) {
            return false;
        }
        long newAllowedNetworkTypes = currentlyAllowedNetworkTypes;
        if (isChecked) {
            newAllowedNetworkTypes = currentlyAllowedNetworkTypes | BITMASK_2G;
            Log.i(LOG_TAG, "Enabling 2g. Allowed network types: " + newAllowedNetworkTypes);
        } else {
            newAllowedNetworkTypes = currentlyAllowedNetworkTypes & ~BITMASK_2G;
            Log.i(LOG_TAG, "Disabling 2g. Allowed network types: " + newAllowedNetworkTypes);
        }
        mTelephonyManager.setAllowedNetworkTypesForReason(
                mTelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G, newAllowedNetworkTypes);
        mMetricsFeatureProvider.action(
                mContext, SettingsEnums.ACTION_2G_ENABLED, isChecked);
        return true;
    }
}
