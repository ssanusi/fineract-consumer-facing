/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.registration.query.service;

import feign.FeignException;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.clients.FineractClientIdentifiersClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.FineractClientIdentifierData;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQuery;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.registration.query.exception.IdentityVerificationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityQueryServiceImpl implements IdentityQueryService {

    private final FineractClientIdentifiersClient identifiersClient;

    @Override
    @Query
    public IdentityVerificationQueryData verifyIdentity(IdentityVerificationQuery query) {
        return fetchIdentifiers(query.getFineractClientId()).stream()
                .filter(matchesDocumentType(query.getDocumentTypeName()))
                .filter(matchesNormalizedKey(query.getDocumentKey()))
                .findFirst()
                .map(i -> IdentityVerificationQueryData.verified(lastFour(i.getDocumentKey())))
                .orElseGet(IdentityVerificationQueryData::denied);
    }

    private List<FineractClientIdentifierData> fetchIdentifiers(Long clientId) {
        try {
            List<FineractClientIdentifierData> result = identifiersClient.getIdentifiers(clientId);
            return result == null ? List.of() : result;
        } catch (FeignException.NotFound e) {
            return List.of();
        } catch (FeignException e) {
            throw new IdentityVerificationException(e);
        }
    }

    private static Predicate<FineractClientIdentifierData> matchesDocumentType(String typeName) {
        return i -> i.getDocumentType() != null && typeName.equals(i.getDocumentType().getName());
    }

    private static Predicate<FineractClientIdentifierData> matchesNormalizedKey(String documentKey) {
        String target = normalize(documentKey);
        return i -> i.getDocumentKey() != null && normalize(i.getDocumentKey()).equals(target);
    }

    private static String normalize(String input) {
        return input.trim().replace("-", "").replace(" ", "").toUpperCase();
    }

    private static String lastFour(String value) {
        String normalized = normalize(value);
        return normalized.length() < 4 ? null : normalized.substring(normalized.length() - 4);
    }
}
