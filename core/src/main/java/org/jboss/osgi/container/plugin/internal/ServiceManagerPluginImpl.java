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
package org.jboss.osgi.container.plugin.internal;

//$Id$

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.RemovingServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ServiceState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.container.util.NoFilter;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;

/**
 * A plugin that manages OSGi services
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class ServiceManagerPluginImpl extends AbstractPlugin implements ServiceManagerPlugin
{
   // Provide logging
   private final Logger log = Logger.getLogger(ServiceManagerPluginImpl.class);

   // The ServiceId generator 
   private AtomicLong identityGenerator = new AtomicLong();
   // The ServiceContainer
   private ServiceContainer serviceContainer;
   // Maps the service interface to the list of registered service names
   private Map<String, List<ServiceName>> serviceNameMap = new ConcurrentHashMap<String, List<ServiceName>>();

   public ServiceManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      serviceContainer = ServiceContainer.Factory.create();
   }

   @Override
   public long getNextServiceId()
   {
      return identityGenerator.incrementAndGet();
   }

   @Override
   public List<ServiceState> getRegisteredServices(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }

   @Override
   public Object getService(AbstractBundle bundleState, ServiceState serviceState)
   {
      Object value = serviceState.getValue();
      if (value instanceof ServiceFactory)
         value = serviceState.getServiceFactoryValue(bundleState);

      return value;
   }

   @Override
   public ServiceState getServiceReference(AbstractBundle bundleState, String clazz)
   {
      List<ServiceState> srefs = getServiceReferencesInternal(bundleState, clazz, null, true);
      if (srefs == null || srefs.isEmpty())
         return null;

      return srefs.get(0);
   }

   @Override
   public List<ServiceState> getServiceReferences(AbstractBundle bundleState, String clazz, String filterStr, boolean checkAssignable)
         throws InvalidSyntaxException
   {
      Filter filter = null;
      if (filterStr != null)
         filter = FrameworkUtil.createFilter(filterStr);

      return getServiceReferencesInternal(bundleState, clazz, filter, true);
   }

   public List<ServiceState> getServiceReferencesInternal(AbstractBundle bundleState, String clazz, Filter filter, boolean checkAssignable)
   {
      List<ServiceState> result = new ArrayList<ServiceState>();
      List<ServiceName> names = serviceNameMap.get(clazz);
      if (names == null)
         return Collections.emptyList();

      if (filter == null)
         filter = NoFilter.INSTANCE;

      for (ServiceName name : names)
      {
         ServiceController<?> controller = serviceContainer.getService(name);
         if (controller == null)
            throw new IllegalStateException("Cannot obtain service for: " + name);

         ServiceState serviceState = (ServiceState)controller.getValue();
         if (filter.match(serviceState))
            result.add(serviceState);
      }
      return Collections.unmodifiableList(result);
   }

   @Override
   public List<ServiceState> getServicesInUse(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public ServiceState registerService(AbstractBundle bundleState, String[] clazzes, Object value, Dictionary properties)
   {
      if (clazzes == null || clazzes.length == 0)
         throw new IllegalArgumentException("Null service classes");

      final boolean traceEnabled = log.isTraceEnabled();

      // A temporary association of the clazz and name
      Map<ServiceName, String> associations = new HashMap<ServiceName, String>();

      final ServiceState serviceState = new ServiceState(bundleState, clazzes, value, properties);
      BatchBuilder batchBuilder = serviceContainer.batchBuilder();
      ServiceName serviceName = null;
      for (String className : clazzes)
      {
         try
         {
            serviceName = createServiceName(className, serviceState.getServiceId());
            Service service = new Service()
            {
               @Override
               public Object getValue() throws IllegalStateException
               {
                  // [TODO] for injection to work this needs to be the Object value
                  return serviceState;
               }

               @Override
               public void start(StartContext context) throws StartException
               {
               }

               @Override
               public void stop(StopContext context)
               {
               }
            };
            log.debug("Register service: " + serviceName);
            batchBuilder.addService(serviceName, service).setInitialMode(Mode.IMMEDIATE);
            associations.put(serviceName, className);
         }
         catch (DuplicateServiceException ex)
         {
            log.error("Cannot register service: " + className, ex);
         }
      }

      // Install a startup listener with every service
      final CountDownLatch serviceStartedLatch = new CountDownLatch(clazzes.length);
      class ServiceStartupListener<S> extends AbstractServiceListener<S>
      {
         @Override
         public void serviceStarted(ServiceController<? extends S> controller)
         {
            if (traceEnabled == true)
               log.trace("Service started: " + controller.getName());

            serviceStartedLatch.countDown();
         }
      }
      batchBuilder.addListener(new ServiceStartupListener());

      try
      {
         batchBuilder.install();

         // Register the name association. We do this here 
         // in case anything went wrong during the install
         for (Entry<ServiceName, String> aux : associations.entrySet())
         {
            bundleState.addOwnedService(serviceState);
            registerServiceName(aux.getValue(), aux.getKey());
            serviceState.addServiceName(aux.getKey());
         }
      }
      catch (ServiceRegistryException ex)
      {
         log.error("Cannot register services: " + Arrays.asList(clazzes), ex);
      }

      // Wait for all services to get started
      try
      {
         if (traceEnabled == true && serviceStartedLatch.getCount() > 0)
            log.trace("Await service startup ...");

         serviceStartedLatch.await();

         if (traceEnabled == true)
            log.trace("All services started");
      }
      catch (InterruptedException ex)
      {
         // ignore
      }

      return serviceState;
   }

   @Override
   public void unregisterService(ServiceState serviceState)
   {
      List<ServiceName> names = serviceState.getServiceNames();
      if (names != null)
      {
         for (ServiceName name : names)
         {
            unregisterService(name);
         }
      }
   }

   // Creates a unique ServiceName
   private ServiceName createServiceName(String className, long serviceId)
   {
      return ServiceName.of("jbosgi", "service", className, new Long(serviceId).toString());
   }

   private void registerServiceName(String className, ServiceName serviceName)
   {
      List<ServiceName> names = serviceNameMap.get(className);
      if (names == null)
      {
         names = new CopyOnWriteArrayList<ServiceName>();
         serviceNameMap.put(className, names);
      }
      names.add(serviceName);
   }

   private void unregisterServiceName(String className, ServiceName serviceName)
   {
      List<ServiceName> names = serviceNameMap.get(className);
      if (names == null)
      {
         log.warn("Cannot obtain service names for: " + className);
         return;
      }

      if (names.remove(serviceName) == false)
         log.warn("Cannot remove [" + serviceName + "] from: " + names);
   }

   @Override
   public boolean ungetService(AbstractBundle bundleState, ServiceState reference)
   {
      // [TODO] ungetService
      return true;
   }

   @Override
   public void unregisterServices(AbstractBundle bundleState)
   {
      Set<ServiceState> ownedServices = bundleState.getOwnedServices();
      for (ServiceState serviceState : ownedServices)
      {
         bundleState.removeOwnedService(serviceState);
         for (ServiceName name : serviceState.getServiceNames())
         {
            unregisterService(name);
         }
      }
   }

   private void unregisterService(ServiceName name)
   {
      log.debug("Unregister service: " + name);

      ServiceController<?> controller = serviceContainer.getService(name);

      // Unregister the service names
      ServiceState serviceState = (ServiceState)controller.getValue();
      String[] clazzes = (String[])serviceState.getProperty(Constants.OBJECTCLASS);
      for (String clazz : clazzes)
         unregisterServiceName(clazz, name);

      // A service is brought DOWN by setting it's mode to NEVER
      // Adding a {@link RemovingServiceListener} does this and will
      // also synchronoulsy remove the service from the registry 
      controller.addListener(new RemovingServiceListener());
   }
}