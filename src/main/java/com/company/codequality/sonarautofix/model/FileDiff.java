package com.company.codequality.sonarautofix.model;

import java.util.List;

import lombok.*;



@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDiff {

	private String relativePath;
	private List<DiffLine> lineDiffs;

}