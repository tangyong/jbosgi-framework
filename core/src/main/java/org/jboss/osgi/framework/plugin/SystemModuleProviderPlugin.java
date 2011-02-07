/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.plugin;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.bundle.SystemBundle;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
public interface SystemModuleProviderPlugin extends Plugin {

    /**
     * Create teh system module
     */
    Module createSystemModule(OSGiModuleLoader moduleLoader, SystemBundle systemBundle) throws ModuleLoadException;

    /**
     * Get the system module
     */
    Module getSystemModule();

    /**
     * Create the framework module
     */
    Module createFrameworkModule(OSGiModuleLoader moduleLoader, SystemBundle systemBundle) throws ModuleLoadException;

    /**
     * Get the framework module
     */
    Module getFrameworkModule();
}