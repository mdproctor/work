package io.casehub.work.progress.rest.core;

import java.util.List;

import io.casehub.work.progress.ProgressInstance;

public record TreeResponse(ProgressInstance root, List<ProgressInstance> descendants) {
}
