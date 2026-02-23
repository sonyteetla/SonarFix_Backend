package com.company.codequality.sonarautofix.model;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueResponse {

    private int total;       
    private int page;       
    private int pageSize;     
    private int totalPages;  
    private List<Issue> issues;
}