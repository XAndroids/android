/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice extends Device {
  private static final Icon ourValidIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.RealDevice);
  private static final Icon ourInvalidIcon = ExecutionUtil.getLiveIndicator(AllIcons.General.Error);

  @NotNull
  static PhysicalDevice newDevice(@NotNull DeviceNameProperties properties,
                                  @NotNull IDevice ddmlibDevice,
                                  @Nullable LaunchCompatibilityChecker checker,
                                  @NotNull KeyToConnectionTimeMap map) {
    return new Builder()
      .setName(getName(properties))
      .setKey(ddmlibDevice.getSerialNumber())
      .setAndroidDevice(new ConnectedAndroidDevice(ddmlibDevice, null))
      .build(checker, map);
  }

  static final class Builder extends Device.Builder<Builder> {
    @NotNull
    @Override
    Builder self() {
      return this;
    }

    @NotNull
    @Override
    PhysicalDevice build(@Nullable LaunchCompatibilityChecker checker, @Nullable KeyToConnectionTimeMap map) {
      return new PhysicalDevice(this, checker, map);
    }
  }

  private PhysicalDevice(@NotNull Builder builder, @Nullable LaunchCompatibilityChecker checker, @Nullable KeyToConnectionTimeMap map) {
    super(builder, checker, map);
  }

  @NotNull
  @VisibleForTesting
  static String getName(@NotNull DeviceNameProperties properties) {
    String manufacturer = properties.getManufacturer();
    String model = properties.getModel();

    if (manufacturer == null && model == null) {
      return "Unknown Device";
    }

    if (manufacturer == null) {
      return model;
    }

    if (model == null) {
      return manufacturer + " Device";
    }

    return manufacturer + ' ' + model;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return isValid() ? ourValidIcon : ourInvalidIcon;
  }

  /**
   * @return true. Physical devices come and go as they are connected and disconnected; there are no instances of this class for
   * disconnected physical devices.
   */
  @Override
  boolean isConnected() {
    return true;
  }

  @NotNull
  @Override
  ImmutableCollection<String> getSnapshots() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    IDevice device = getDdmlibDevice();
    assert device != null;

    return DeploymentApplicationService.getInstance().getVersion(device);
  }

  @Override
  void addTo(@NotNull DeviceFutures futures, @NotNull Project project, @Nullable String snapshot) {
    futures.getDevices().add(getAndroidDevice());
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    Device device = (Device)object;

    return getName().equals(device.getName()) &&
           isValid() == device.isValid() &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), isValid(), getKey(), getConnectionTime(), getAndroidDevice());
  }
}
