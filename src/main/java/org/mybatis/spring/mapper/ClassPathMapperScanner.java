/*
 * Copyright 2010-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.mapper;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.NativeDetector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

/**
 * A {@link ClassPathBeanDefinitionScanner} that registers Mappers by {@code basePackage}, {@code annotationClass}, or
 * {@code markerInterface}. If an {@code annotationClass} and/or {@code markerInterface} is specified, only the
 * specified types will be searched (searching for all interfaces will be disabled).
 * <p>
 * This functionality was previously a private class of {@link MapperScannerConfigurer}, but was broken out in version
 * 1.2.0.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * Mapper接口扫描器
 * 扫描Mapper接口生成MapperFactoryBean。用来生成Mapper接口的代理对象
 * @since 1.2.0
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathMapperScanner.class);

  // Copy of FactoryBean#OBJECT_TYPE_ATTRIBUTE which was added in Spring 5.2
  static final String FACTORY_BEAN_OBJECT_TYPE = "factoryBeanObjectType";

  private boolean addToConfig = true;

  private boolean lazyInitialization;

  private boolean printWarnLogIfNotFoundMappers = true;

  private SqlSessionFactory sqlSessionFactory;

  private SqlSessionTemplate sqlSessionTemplate;

  private String sqlSessionTemplateBeanName;

  private String sqlSessionFactoryBeanName;

  private Class<? extends Annotation> annotationClass;

  private Class<?> markerInterface;

  // 生成mapper代码类的对象
  private Class<? extends MapperFactoryBean> mapperFactoryBeanClass = MapperFactoryBean.class;

  private String defaultScope;
  private List<TypeFilter> excludeFilters;

  public ClassPathMapperScanner(BeanDefinitionRegistry registry, Environment environment) {
    super(registry, false, environment);
    setIncludeAnnotationConfig(!AotDetector.useGeneratedArtifacts());
    setPrintWarnLogIfNotFoundMappers(!NativeDetector.inNativeImage());
  }

  /**
   * @deprecated Please use the {@link #ClassPathMapperScanner(BeanDefinitionRegistry, Environment)}.
   */
  @Deprecated(since = "3.0.4", forRemoval = true)
  public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
    super(registry, false);
    setIncludeAnnotationConfig(!AotDetector.useGeneratedArtifacts());
    setPrintWarnLogIfNotFoundMappers(!NativeDetector.inNativeImage());
  }

  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  /**
   * Set whether enable lazy initialization for mapper bean.
   * <p>
   * Default is {@code false}.
   * </p>
   *
   * @param lazyInitialization
   *          Set the @{code true} to enable
   *
   * @since 2.0.2
   */
  public void setLazyInitialization(boolean lazyInitialization) {
    this.lazyInitialization = lazyInitialization;
  }

  /**
   * Set whether print warning log if not found mappers that matches conditions.
   * <p>
   * Default is {@code true}. But {@code false} when running in native image.
   * </p>
   *
   * @param printWarnLogIfNotFoundMappers
   *          Set the @{code true} to print
   *
   * @since 3.0.1
   */
  public void setPrintWarnLogIfNotFoundMappers(boolean printWarnLogIfNotFoundMappers) {
    this.printWarnLogIfNotFoundMappers = printWarnLogIfNotFoundMappers;
  }

  public void setMarkerInterface(Class<?> markerInterface) {
    this.markerInterface = markerInterface;
  }

  public void setExcludeFilters(List<TypeFilter> excludeFilters) {
    this.excludeFilters = excludeFilters;
  }

  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
  }

  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
  }

  /**
   * @deprecated Since 2.0.1, Please use the {@link #setMapperFactoryBeanClass(Class)}.
   */
  @Deprecated
  public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
    this.mapperFactoryBeanClass = mapperFactoryBean == null ? MapperFactoryBean.class : mapperFactoryBean.getClass();
  }

  /**
   * Set the {@code MapperFactoryBean} class.
   *
   * @param mapperFactoryBeanClass
   *          the {@code MapperFactoryBean} class
   *
   * @since 2.0.1
   */
  public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
    this.mapperFactoryBeanClass = mapperFactoryBeanClass == null ? MapperFactoryBean.class : mapperFactoryBeanClass;
  }

  /**
   * Set the default scope of scanned mappers.
   * <p>
   * Default is {@code null} (equiv to singleton).
   * </p>
   *
   * @param defaultScope
   *          the scope
   *
   * @since 2.0.6
   */
  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }

  /**
   * Configures parent scanner to search for the right interfaces. It can search for all interfaces or just for those
   * that extends a markerInterface or/and those annotated with the annotationClass
   */
  public void registerFilters() {
    boolean acceptAllInterfaces = true;

    // if specified, use the given annotation and / or marker interface
    if (this.annotationClass != null) {
      addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
      acceptAllInterfaces = false;
    }

    // override AssignableTypeFilter to ignore matches on the actual marker interface
    if (this.markerInterface != null) {
      addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
        @Override
        protected boolean matchClassName(String className) {
          return false;
        }
      });
      acceptAllInterfaces = false;
    }

    if (acceptAllInterfaces) {
      // default include filter that accepts all classes
      addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
    }

    // exclude package-info.java
    addExcludeFilter((metadataReader, metadataReaderFactory) -> {
      String className = metadataReader.getClassMetadata().getClassName();
      return className.endsWith("package-info");
    });

    // exclude types declared by MapperScan.excludeFilters
    if (excludeFilters != null && excludeFilters.size() > 0) {
      for (TypeFilter excludeFilter : excludeFilters) {
        addExcludeFilter(excludeFilter);
      }
    }
  }

  /**
   * Calls the parent search that will search and register all the candidates. Then the registered objects are post
   * processed to set them as MapperFactoryBeans
   */
  @Override
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    // 所有mapper接口的beanDefinition
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    if (beanDefinitions.isEmpty()) {
      if (printWarnLogIfNotFoundMappers) {
        LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages)
            + "' package. Please check your configuration.");
      }
    } else {
      // 处理mapper接口的beanDefinition
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }

  /**
   * 回调方法，用于处理扫描到的BeanDefinition
   * 重新赋值Mapper的BeanDefinition
   * @param beanDefinitions
   */
  private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    AbstractBeanDefinition definition;
    // 获取BeanDefinitionRegistry注册器
    BeanDefinitionRegistry registry = getRegistry();
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (AbstractBeanDefinition) holder.getBeanDefinition();
      boolean scopedProxy = false;
      // 单例应该走不到这里
      if (ScopedProxyFactoryBean.class.getName().equals(definition.getBeanClassName())) {
        definition = (AbstractBeanDefinition) Optional
            .ofNullable(((RootBeanDefinition) definition).getDecoratedDefinition())
            .map(BeanDefinitionHolder::getBeanDefinition).orElseThrow(() -> new IllegalStateException(
                "The target bean definition of scoped proxy bean not found. Root bean definition[" + holder + "]"));
        scopedProxy = true;
      }
      // mapper接口的类名
      String beanClassName = definition.getBeanClassName();
      LOGGER.debug(() -> "Creating MapperFactoryBean with name '" + holder.getBeanName() + "' and '" + beanClassName
          + "' mapperInterface");

      // the mapper interface is the original class of the bean
      // but, the actual class of the bean is MapperFactoryBean
      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59
      try {
        // 获取mapper接口的Class对象
        Class<?> beanClass = Resources.classForName(beanClassName);
        // Attribute for MockitoPostProcessor
        // https://github.com/mybatis/spring-boot-starter/issues/475
        //public interface FactoryBean<T> {
        //    String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";
        definition.setAttribute(FACTORY_BEAN_OBJECT_TYPE, beanClass);
        // for spring-native
        // 设置MapperFactoryBean里的mapperInterface属性为mapper接口的Class对象
        definition.getPropertyValues().add("mapperInterface", beanClass);
      } catch (ClassNotFoundException ignore) {
        // ignore
      }

      // 设置beanDefinition的类信息为MapperFactoryBean
      definition.setBeanClass(this.mapperFactoryBeanClass);

      // 设置MapperFactoryBean的属性addToConfig
      definition.getPropertyValues().add("addToConfig", this.addToConfig);

      // 设置MapperFactoryBean的属性sqlSessionFactory。在父类里
      boolean explicitFactoryUsed = false;
      if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
        definition.getPropertyValues().add("sqlSessionFactory",
            new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionFactory != null) {
        definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
        explicitFactoryUsed = true;
      }

      // 设置MapperFactoryBean的属性sqlSessionTemplate。在父类里
      if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate",
            new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionTemplate != null) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
        explicitFactoryUsed = true;
      }

      if (!explicitFactoryUsed) {
        LOGGER.debug(() -> "Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
      }

      definition.setLazyInit(lazyInitialization);

      if (scopedProxy) {
        continue;
      }

      if (ConfigurableBeanFactory.SCOPE_SINGLETON.equals(definition.getScope()) && defaultScope != null) {
        definition.setScope(defaultScope);
      }

      // 不是单例才注册
      if (!definition.isSingleton()) {
        BeanDefinitionHolder proxyHolder = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (registry.containsBeanDefinition(proxyHolder.getBeanName())) {
          registry.removeBeanDefinition(proxyHolder.getBeanName());
        }

        // 注册MapperFactoryBean的BeanDefinition
        // beanName是beanDefinition的名字，其实就是mapper接口的类名
        registry.registerBeanDefinition(proxyHolder.getBeanName(), proxyHolder.getBeanDefinition());
      }

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
    return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
    if (super.checkCandidate(beanName, beanDefinition)) {
      return true;
    } else {
      LOGGER.warn(() -> "Skipping MapperFactoryBean with name '" + beanName + "' and '"
          + beanDefinition.getBeanClassName() + "' mapperInterface" + ". Bean already defined with the same name!");
      return false;
    }
  }

}
