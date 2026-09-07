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

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyBoolean;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.bluetooth.Util;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.tests.bluetooth.StaticMockitoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class AdapterBinderLogicalOffTest {
    @Rule public final StaticMockitoRule mMocks = new StaticMockitoRule(Util.class);
    @Mock AdapterService mService;
    private AdapterBinder mBinder;

    private void callerAllowed(boolean allowed) {
        ExtendedMockito.doReturn(allowed).when(() -> Util.callerIsSystemOrActiveOrManagedUser(
                mService, Util.BT_PREFIX + "AdapterBinder", "onToBleOnWithPolicy"));
    }

    @Before public void setup() {
        doReturn(true).when(mService).isAvailable();
        callerAllowed(true);
        mBinder = new AdapterBinder(mService);
    }

    @Test public void forwardsHoldAndRevoke() {
        mBinder.onToBleOnWithPolicy(true);
        mBinder.onToBleOnWithPolicy(false);
        verify(mService).onToBleOnWithPolicy(true);
        verify(mService).onToBleOnWithPolicy(false);
    }

    @Test public void permissionFailureCannotForward() {
        doThrow(new SecurityException()).when(mService)
                .enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED, null);
        assertThrows(SecurityException.class, () -> mBinder.onToBleOnWithPolicy(true));
        verify(mService, never()).onToBleOnWithPolicy(anyBoolean());
    }

    @Test public void wrongUserCannotForward() {
        callerAllowed(false);
        mBinder.onToBleOnWithPolicy(true);
        verify(mService, never()).onToBleOnWithPolicy(anyBoolean());
    }
}
