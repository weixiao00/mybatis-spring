/*
 * Copyright 2010-2022 the original author or authors.
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

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a SqlSessionFactory or a
 * pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 * 有两个主要的bean
 *   1. 一个是创建sqlSessionFactory的bean。名字叫做sqlSessionFactoryBean，实现了两个Spring的类，一个InitializingBean，FactoryBean
 *   InitializingBean重写的方法创建sqlSessioniFactory
 *   FactoryBean重写getObject方法返回sqlSessioniFactory
 *
 *   2. mapperFactoryBean是一个mapper接口会有一个mapperFactoryBean。实现了FactoryBean方法
 *   通过重写getObject方法返回Mapper接口的代理对象。调用的时候就走到了代理的方法
 *   mapperFactoryBean也是实现了一个SqlSessionDaoSupport实现了daoSupport实现了InitializingBean。创建了mapper的MapperProxyFactory。为了走代码的方法。同时也包含了sqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  // 需要拦截的接口类信息
  // 主要是Mapper接口
  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   * 这里其实也是通过父类实现了InitializingBean接口
   * 通过afterPropertiesSet方法来调用的
   */
  @Override
  protected void checkDaoConfig() {
    // 判断sqlSessionTemplate是否为空
    super.checkDaoConfig();

    // 代码的目标类不能为空
    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    // 获取全局配置
    Configuration configuration = getSqlSession().getConfiguration();
    // 判断是否已经注册了这个接口，是否有这个目标类的MapperProxyFactory代理工厂
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        // 创建一个MapperProxyFactory代理工厂添加到Map里
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   * 通过getObject方法返回Mapper接口的代理对象
   */
  /**
   * 具体流程在这里描述一下
   * 1. 通过getSqlSession()获取SqlSessionTemplate对象
   * 2. 通过SqlSessionTemplate对象获取Mapper接口的代理对象（mybatis的流程了）MapperProxyFactory->MapperProxy
   * 3. 返回Mapper接口的代理对象
   * 4. proxy对象调用方法的时候会走到MapperProxy的invoke方法
   * 5. invoke方法会调用SqlSessionTemplate的selectList方法
   * 6. selectList方法会调用SqlSessionTemplate里的sqlSessionProxy代码对象的selectList方法
   * 7. 会进入到SqlSessionInterceptor的invoke方法
   * 8. 从ThreadLocal里获取defaultSqlSession对象(线程安全的)
   * 9. 没有获取到defaultSqlSession对象的时候会创建一个defaultSqlSession对象，放入到ThreadLocal里
   * 10. 反射调用defaultSqlSession对象的selectList方法
   * 11. 之后就是mybatis的流程了(defaultSqlSession调用流程)
   */
  @Override
  public T getObject() throws Exception {
    // 获取Mapper接口的代理对象
    // 最后会创建出来一个MapperProxy对象，里边会有invoke方法
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  // ------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface
   *          class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means it must have been included in
   * mybatis-config.xml.
   * <p>
   * If it is true, the mapper will be added to MyBatis in the case it is not already registered.
   * <p>
   * By default addToConfig is true.
   *
   * @param addToConfig
   *          a flag that whether add mapper to MyBatis or not
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
