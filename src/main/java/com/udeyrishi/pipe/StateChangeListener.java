package com.udeyrishi.pipe;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
public interface StateChangeListener {
    void onStateChanged(@NotNull UUID uuid, @NotNull State previousState, @NotNull State newState);
}
