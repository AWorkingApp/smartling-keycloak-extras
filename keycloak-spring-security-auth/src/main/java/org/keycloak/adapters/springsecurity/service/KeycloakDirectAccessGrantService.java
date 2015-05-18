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

package org.keycloak.adapters.springsecurity.service;

import org.keycloak.OAuth2Constants;
import org.keycloak.RSATokenVerifier;
import org.keycloak.VerificationException;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.springsecurity.AdapterDeploymentContextBean;
import org.keycloak.adapters.springsecurity.service.context.KeycloakConfidentialClientRequestFactory;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Arrays;

/**
 * Supports Keycloak's direct access grants API using direct REST invocations to obtain
 * an access token.
 *
 * @author <a href="mailto:srossillo@smartling.com">Scott Rossillo</a>
 */
@Component
public class KeycloakDirectAccessGrantService implements DirectAccessGrantService {

    @Autowired
    private AdapterDeploymentContextBean adapterDeploymentContextBean;

    @Autowired
    private KeycloakConfidentialClientRequestFactory requestFactory;

    private KeycloakDeployment deployment;
    protected RestTemplate template;

    @PostConstruct
    public void init() {
        deployment = adapterDeploymentContextBean.getDeployment();
        // make sure to use Jackson 1.9.x message converter in case 2.x is on the classpath
        template = new RestTemplate(requestFactory);

        for (HttpMessageConverter converter : template.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                template.getMessageConverters().remove(converter);
                template.getMessageConverters().add(new MappingJacksonHttpMessageConverter());
                break;
            }
        }
    }

    @Override
    public RefreshableKeycloakSecurityContext login(String username, String password) throws VerificationException {

        final MultiValueMap<String,String> body = new LinkedMultiValueMap<>();
        final HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        body.set("username", username);
        body.set("password", password);
        body.set(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD);

        AccessTokenResponse response = template.postForObject(deployment.getTokenUrl(), new HttpEntity<>(body, headers), AccessTokenResponse.class);

        return createContext(response);
    }

    protected RefreshableKeycloakSecurityContext createContext(AccessTokenResponse response) throws VerificationException {
        String tokenString = response.getToken();
        String idTokenString = response.getIdToken();
        AccessToken accessToken = RSATokenVerifier.verifyToken(tokenString, deployment.getRealmKey(), deployment.getRealmInfoUrl());
        IDToken idToken;

        JWSInput input = new JWSInput(idTokenString);
        try {
            idToken = input.readJsonContent(IDToken.class);
        } catch (IOException e) {
            throw new VerificationException("Unable to verify ID token", e);
        }

        return new RefreshableKeycloakSecurityContext(deployment, null, tokenString, accessToken, idTokenString, idToken, response.getRefreshToken());
    }
}
