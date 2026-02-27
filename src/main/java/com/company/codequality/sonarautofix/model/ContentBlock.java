package com.company.codequality.sonarautofix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlock {

    private String type;   // heading | paragraph | unordered_list | ordered_list
    private String text;

    private List<String> items;

    public ContentBlock(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public ContentBlock(String type, List<String> items) {
        this.type = type;
        this.items = items;
    }
}