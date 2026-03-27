package com.company.codequality.sonarautofix.model;

import lombok.Data;

@Data
public class VariableRenameRequest {
    private String scanId;
    private String oldName;
    private String newName;
    private String csvPath;
}
