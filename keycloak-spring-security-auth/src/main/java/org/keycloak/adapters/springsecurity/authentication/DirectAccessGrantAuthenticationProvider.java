/*
 * Copyright 2015 Smartling, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.adapters.springsecurity.authentication;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.VerificationException;
import org.keycloak.adapters.AdapterUtils;
import org.keycloak.adapters.KeycloakAccount;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.springsecurity.AdapterDeploymentContextBean;
import org.keycloak.adapters.springsecurity.account.KeycloakRole;
import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.keycloak.adapters.springsecurity.service.DirectAccessGrantService;
import org.keycloak.adapters.springsecurity.token.DirectAccessGrantToken;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * {@link AuthenticationProvider} implementing the OAuth2 resource owner password credentials
 * grant for clients secured by Keycloak.
 *
 * <p>
 * The resource owner password credentials grant type is suitable in
 * cases where the resource owner has a trust relationship with the
 * client, such as the device operating system or a highly privileged
 * application.
 * </p>
 *
 * @author <a href="mailto:srossillo@smartling.com">Scott Rossillo</a>
 */
public class DirectAccessGrantAuthenticationProvider implements AuthenticationProvider {

    private AdapterDeploymentContextBean adapterDeploymentContextBean;
    private DirectAccessGrantService directAccessGrantService;
    private GrantedAuthoritiesMapper grantedAuthoritiesMapper = null;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();
        RefreshableKeycloakSecurityContext context;
        KeycloakAuthenticationToken token;
        Collection<? extends GrantedAuthority> authorities;

        try {
            context = directAccessGrantService.login(username, password);
            authorities = this.createGrantedAuthorities(context);
            token = new KeycloakAuthenticationToken(createAccount(context), authorities);
        } catch (VerificationException e) {
            throw new BadCredentialsException("Unable to validate token", e);
        } catch (Exception e) {
            throw new AuthenticationServiceException("Error authenticating with Keycloak server", e);
        }

        return token;
    }

    private KeycloakAccount createAccount(RefreshableKeycloakSecurityContext context) {
        Assert.notNull(context);
        Set<String> roles = AdapterUtils.getRolesFromSecurityContext(context);
        KeycloakPrincipal<RefreshableKeycloakSecurityContext> principal =
                AdapterUtils.createPrincipal(adapterDeploymentContextBean.getDeployment(), context);

        return new SimpleKeycloakAccount(principal, roles, context);
    }

    private Collection<? extends GrantedAuthority> createGrantedAuthorities(RefreshableKeycloakSecurityContext context) {
        List<KeycloakRole> grantedAuthorities = new ArrayList<>();

        for (String role : AdapterUtils.getRolesFromSecurityContext(context)) {
            grantedAuthorities.add(new KeycloakRole(role));
        }

        return mapAuthorities(Collections.unmodifiableList(grantedAuthorities));
    }

    private Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (grantedAuthoritiesMapper == null) {
            return authorities;
        }
        return grantedAuthoritiesMapper.mapAuthorities(authorities);
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return DirectAccessGrantToken.class.isAssignableFrom(authentication)
                || UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Required
    public void setAdapterDeploymentContextBean(AdapterDeploymentContextBean adapterDeploymentContextBean) {
        this.adapterDeploymentContextBean = adapterDeploymentContextBean;
    }

    @Required
    public void setDirectAccessGrantService(DirectAccessGrantService directAccessGrantService) {
        this.directAccessGrantService = directAccessGrantService;
    }

    /**
     * Set the optional {@link GrantedAuthoritiesMapper} for this {@link AuthenticationProvider}.
     *
     * @param grantedAuthoritiesMapper the <code>GrantedAuthoritiesMapper</code> to use
     */
    public void setGrantedAuthoritiesMapper(GrantedAuthoritiesMapper grantedAuthoritiesMapper) {
        this.grantedAuthoritiesMapper = grantedAuthoritiesMapper;
    }
}
