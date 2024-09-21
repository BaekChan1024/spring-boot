/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.build.bom.bomr;

import java.io.StringReader;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.springframework.boot.build.bom.bomr.version.DependencyVersion;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * A {@link VersionResolver} that examines {@code maven-metadata.xml} to determine the
 * available versions.
 *
 * @author Andy Wilkinson
 */
final class MavenMetadataVersionResolver implements VersionResolver {

	private final RestTemplate rest;

	private final Collection<MavenArtifactRepository> repositories;

	MavenMetadataVersionResolver(Collection<MavenArtifactRepository> repositories) {
		this(new RestTemplate(Collections.singletonList(new StringHttpMessageConverter())), repositories);
	}

	MavenMetadataVersionResolver(RestTemplate restTemplate, Collection<MavenArtifactRepository> repositories) {
		this.rest = restTemplate;
		this.repositories = repositories;
	}

	@Override
	public SortedSet<DependencyVersion> resolveVersions(String groupId, String artifactId) {
		Set<String> versions = new HashSet<>();
		for (MavenArtifactRepository repository : this.repositories) {
			versions.addAll(resolveVersions(groupId, artifactId, repository));
		}
		return versions.stream().map(DependencyVersion::parse).collect(Collectors.toCollection(TreeSet::new));
	}

	private Set<String> resolveVersions(String groupId, String artifactId, MavenArtifactRepository repository) {
		Set<String> versions = new HashSet<>();
		URI url = repository.getUrl().resolve(groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml");
		try {
			HttpHeaders headers = new HttpHeaders();
			String username = repository.getCredentials().getUsername();
			if (username != null) {
				headers.setBasicAuth(username, repository.getCredentials().getPassword());
			}
			HttpEntity<Void> request = new HttpEntity<>(headers);
			String metadata = this.rest.exchange(url, HttpMethod.GET, request, String.class).getBody();
			Document metadataDocument = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(new InputSource(new StringReader(metadata)));
			NodeList versionNodes = (NodeList) XPathFactory.newInstance()
				.newXPath()
				.evaluate("/metadata/versioning/versions/version", metadataDocument, XPathConstants.NODESET);
			for (int i = 0; i < versionNodes.getLength(); i++) {
				versions.add(versionNodes.item(i).getTextContent());
			}
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
				System.err.println("Failed to download maven-metadata.xml for " + groupId + ":" + artifactId + " from "
						+ url + ": " + ex.getMessage());
			}
		}
		catch (Exception ex) {
			System.err.println("Failed to resolve versions for module " + groupId + ":" + artifactId + " in repository "
					+ repository + ": " + ex.getMessage());
		}
		return versions;
	}

}
