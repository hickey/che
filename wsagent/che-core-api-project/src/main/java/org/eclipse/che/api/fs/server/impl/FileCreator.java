/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.fs.server.impl;

import static com.google.common.io.ByteStreams.toByteArray;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.fileupload.FileItem;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FileCreator {

  private static final Logger LOG = LoggerFactory.getLogger(FileCreator.class);

  private final SimpleFsPathResolver pathResolver;

  @Inject
  public FileCreator(SimpleFsPathResolver pathResolver) {
    this.pathResolver = pathResolver;
  }

  public void create(String wsPath) throws NotFoundException, ConflictException, ServerException {
    createInternally(wsPath, null);
  }

  public void create(String wsPath, InputStream content)
      throws NotFoundException, ConflictException, ServerException {
    createInternally(wsPath, () -> toByteArray(content));
  }

  public void create(String wsPath, String content)
      throws NotFoundException, ConflictException, ServerException {
    createInternally(wsPath, content::getBytes);
  }

  public void create(String wsPath, byte[] content)
      throws NotFoundException, ConflictException, ServerException {
    createInternally(wsPath, () -> content);
  }

  public void create(String parentWsPath, Iterator<FileItem> content)
      throws NotFoundException, ConflictException, ServerException {
    Path parentFsPath = pathResolver.toFsPath(parentWsPath);

    if (!parentFsPath.toFile().exists()) {
      throw new NotFoundException("FS item '" + parentFsPath.toString() + "' does not exist");
    }

    FileItem contentItem = null;
    String fileName = null;
    boolean overwrite = false;

    while (content.hasNext()) {
      FileItem item = content.next();
      if (!item.isFormField()) {
        if (contentItem == null) {
          contentItem = item;
        } else {
          throw new ServerException("Expected no more than one file to upload");
        }
      } else if ("name".equals(item.getFieldName())) {
        fileName = item.getString().trim();
      } else if ("overwrite".equals(item.getFieldName())) {
        overwrite = Boolean.parseBoolean(item.getString().trim());
      }
    }

    if (contentItem == null) {
      throw new ServerException("Cannot find file for upload. ");
    }

    if (fileName == null || fileName.isEmpty()) {
      fileName = contentItem.getName();
    }

    Path fsPath = parentFsPath.resolve(fileName);
    String wsPath = pathResolver.toWsPath(fsPath);

    if (parentFsPath.toFile().exists()) {
      throw new ConflictException("FS item '" + parentFsPath.toString() + "' already exists");
    }
    if (!overwrite) {
      if (fsPath.toFile().exists()) {
        throw new ConflictException("FS item '" + fsPath.toString() + "' already exists");
      }
    }

    try {
      createQuietly(wsPath, contentItem.getInputStream());
    } catch (IOException e) {
      throw new ServerException("Can't read content for file: " + wsPath, e);
    }
  }

  public boolean createQuietly(String wsPath) {
    return createInternallyAndQuietly(wsPath, null);
  }

  public boolean createQuietly(String wsPath, InputStream content) {
    return createInternallyAndQuietly(wsPath, () -> toByteArray(content));
  }

  public boolean createQuietly(String wsPath, String content) {
    return createInternallyAndQuietly(wsPath, content::getBytes);
  }

  public boolean createQuietly(String wsPath, byte[] content) {
    return createInternallyAndQuietly(wsPath, () -> content);
  }

  public boolean createQuietly(String parentWsPath, Iterator<FileItem> content) {
    Path parentFsPath = pathResolver.toFsPath(parentWsPath);

    FileItem contentItem = null;
    String fileName = null;

    while (content.hasNext()) {
      FileItem item = content.next();
      if (!item.isFormField()) {
        if (contentItem == null) {
          contentItem = item;
        } else {
          String message = "Expected no more than one file to upload";
          LOG.error(message);
        }
      } else if ("name".equals(item.getFieldName())) {
        fileName = item.getString().trim();
      }
    }

    if (contentItem == null) {
      LOG.error("Cannot find file content to upload");
      return false;
    }

    if (fileName == null || fileName.isEmpty()) {
      fileName = contentItem.getName();
    }

    Path fsPath = parentFsPath.resolve(fileName);
    String wsPath = pathResolver.toWsPath(fsPath);

    try {
      return createQuietly(wsPath, contentItem.getInputStream());
    } catch (IOException e) {
      LOG.error("Can't read content for file: " + wsPath);
    }
    return false;
  }

  private void createInternally(
      String wsPath, SupplierWithException<byte[], IOException> contentSupplier)
      throws NotFoundException, ConflictException, ServerException {
    Path fsPath = pathResolver.toFsPath(wsPath);
    try {
      if (contentSupplier != null) {
        byte[] content = contentSupplier.get();
        Files.write(fsPath, content);
      } else {
        Files.createFile(fsPath);
      }
    } catch (IOException e) {
      throw new ServerException("Failed to create file: " + wsPath, e);
    }
  }

  private boolean createInternallyAndQuietly(
      String wsPath, SupplierWithException<byte[], IOException> contentSupplier) {
    Path fsPath = pathResolver.toFsPath(wsPath);

    try {
      Files.deleteIfExists(fsPath);
      Files.createDirectories(fsPath.getParent());

      createInternally(wsPath, contentSupplier);

      return true;
    } catch (IOException | ConflictException | NotFoundException | ServerException e) {
      LOG.error("Failed to quietly create file: " + wsPath, e);
      return false;
    }
  }
}
