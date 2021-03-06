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
package org.eclipse.che.selenium.core.provider;

import static java.lang.String.format;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.inject.Named;

/** @author Dmytro Nochevnov */
@Singleton
public class CheTestSvnRepo2Provider implements TestSvnRepo2Provider {
  @Inject
  @Named("sys.docker.interface.ip")
  private String localSvnServerHost;

  @Inject
  @Named("sys.svn.server.port")
  private int localSvnServerPort;

  @Inject(optional = true)
  @Named("svn.repo_2.url")
  private String svnRepo2Url;

  @Override
  public String get() {
    if (svnRepo2Url != null) {
      return svnRepo2Url;
    }

    return getLocalServerUrl() + "qa2";
  }

  private String getLocalServerUrl() {
    return format("http://%s:%s/svn/", localSvnServerHost, localSvnServerPort);
  }
}
