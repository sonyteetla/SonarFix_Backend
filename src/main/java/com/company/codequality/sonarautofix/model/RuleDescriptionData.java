package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RuleDescriptionData {

    private final List<ContentBlock> whyBlocks;

    public List<ContentBlock> getWhyBlocks() {
        return whyBlocks;
    }
}