package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleDescriptionData {

    private List<ContentBlock> whyBlocks;
    private List<ContentBlock> fixBlocks;

    private String nonCompliantExample;
    private String compliantExample;
}