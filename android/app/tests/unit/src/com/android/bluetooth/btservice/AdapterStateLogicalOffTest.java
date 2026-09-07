/* Copyright (C) 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.bluetooth.btservice;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.State;
import android.os.SystemProperties;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.bluetooth.flags.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.tests.bluetooth.StaticMockitoRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_ONLY_START_SCAN_DURING_BLE_ON)
@DisableFlags(Flags.FLAG_SYSTEM_SERVER_MIGRATE_BMS_TO_KOTLIN)
public class AdapterStateLogicalOffTest {
    @Rule public final SetFlagsRule mFlags = new SetFlagsRule();
    @Rule public final StaticMockitoRule mMocks = new StaticMockitoRule(SystemProperties.class);
    @Mock AdapterService mService;
    private final TestLooper mLooper = new TestLooper();
    private AdapterState mState;

    private void event(int what, int arg) {
        mState.sendMessage(what, arg);
        mLooper.dispatchAll();
    }

    private void start(boolean enabled) {
        ExtendedMockito.doReturn(enabled).when(() -> SystemProperties.getBoolean(
                "bluetooth.framework.keep_stack_alive_on_user_off", false));
        mState = new AdapterState(mService, mLooper.getLooper());
        mLooper.dispatchAll();
        event(AdapterState.BLE_TURN_ON, 0);
        event(AdapterState.BLE_STARTED, 0);
        event(AdapterState.USER_TURN_ON, 0);
        event(AdapterState.BREDR_STARTED, 0);
        assertThat(mState.getState()).isEqualTo(State.ON);
    }

    @After public void stop() {
        if (mState != null) { mState.doQuit(); mLooper.dispatchAll(); }
    }

    @Test public void logicalOff_stopsProfilesWithoutDisablingBle() {
        start(true);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        verify(mService).stopProfileServices();
        event(AdapterState.BREDR_STOPPED, 0);
        assertThat(mState.getState()).isEqualTo(State.BLE_ON);
        verify(mService).disconnectAllAcls();
        verify(mService, never()).bringDownBle();
        event(AdapterState.USER_TURN_ON, 0);
        assertThat(mState.getState()).isEqualTo(State.TURNING_ON);
    }

    @Test public void defaultOff_evenPolicyRequestTearsDown() {
        start(false);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        event(AdapterState.BREDR_STOPPED, 0);
        assertThat(mState.getState()).isEqualTo(State.BLE_TURNING_OFF);
        verify(mService).bringDownBle();
    }

    @Test public void revokeBeforeCompletion_lateTrueCannotRearm() {
        start(true);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 0);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        event(AdapterState.BREDR_STOPPED, 0);
        assertThat(mState.getState()).isEqualTo(State.BLE_TURNING_OFF);
        verify(mService).bringDownBle();
    }

    @Test public void revokeAfterCompletion_doesNotWaitForManagerCallback() {
        start(true);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        event(AdapterState.BREDR_STOPPED, 0);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 0);
        assertThat(mState.getState()).isEqualTo(State.BLE_TURNING_OFF);
        verify(mService).bringDownBle();
    }

    @Test public void profileTimeout_alwaysPhysicallyTearsDown() {
        start(true);
        event(AdapterState.USER_TURN_OFF_WITH_POLICY, 1);
        event(AdapterState.BREDR_STOP_TIMEOUT, 0);
        assertThat(mState.getState()).isEqualTo(State.BLE_TURNING_OFF);
        verify(mService).bringDownBle();
    }
}
