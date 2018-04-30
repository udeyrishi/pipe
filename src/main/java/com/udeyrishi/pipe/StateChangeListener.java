package com.udeyrishi.pipe;

import org.jetbrains.annotations.NotNull;

/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
public interface StateChangeListener {
    void onStateChanged(@NotNull State previousState, @NotNull State newState);
}
