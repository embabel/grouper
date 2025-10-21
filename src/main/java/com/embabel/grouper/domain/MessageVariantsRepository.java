package com.embabel.grouper.domain;

import org.springframework.lang.Nullable;

public interface MessageVariantsRepository {

    @Nullable
    Model.MessageVariants findByName(String name);
}
