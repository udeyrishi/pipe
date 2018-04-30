package com.udeyrishi.pipe;

/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
public interface Step<T> {
    T doStep(T input);
}
