#!/usr/bin/php
<?php
# Libre BusTO
# Copyright (C) 2023 Valerio Bozzolan and contributors
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License

// load configuration
require 'config.php';

// load Arcanist stuff
require ARCANIST_INIT;

// repository pathname
if( !defined( 'REPO_PATH' ) ) {
	define( 'REPO_PATH', __DIR__ . '/..' );
}

// load gradle stuff
$gradle_content = file_get_contents( REPO_PATH . '/build.gradle' );
preg_match( '/versionCode +([0-9]+)/', $gradle_content, $matches );
$version_code = $matches[1] ?? null;

// no code no party
if( !$version_code ) {
	throw new Exception( "unable to extract version code" );
}

// no internationalization no party
$I18N_raw = file_get_contents( __DIR__ . '/i18n.json' );
if( $I18N_raw === false ) {
	throw new Exception("cannot load file i18n");
}

// no JSON no party
$I18N = json_decode( $I18N_raw, true );
if( $I18N === null ) {
	throw new Exception( "cannot JSON-decode file" );
}

// read first argument
$diff_id = $argv[1] ?? null;

// no diff ID no party
if( !$diff_id ) {
	echo "Please specify Differential ID like D66\n";
	exit( 1 );
}

$client = new ConduitClient( PHABRICATOR_HOME );
$client->setConduitToken( CONDUIT_API_TOKEN );

$tasks = [];
$tasks_phid = [];

// cache with users by phids
$USERS_BY_PHID = [];

// https://gitpull.it/conduit/method/edge.search/
$edge_api_parameters = [
	'sourcePHIDs' => [ $diff_id ],
	'types'       => [ 'revision.task' ],
];

// find Tasks attached to Diff patch
$edge_result = $client->callMethodSynchronous( 'edge.search', $edge_api_parameters );
foreach( $edge_result['data'] as $data ) {
	$tasks_phid[] = $data['destinationPHID'];
}

// https://gitpull.it/conduit/method/maniphest.search/
$maniphest_api_parameters = [
	'constraints' => [
		'phids' => $tasks_phid,
	],
];

// query Tasks info
$maniphest_result = $client->callMethodSynchronous( 'maniphest.search', $maniphest_api_parameters );
foreach( $maniphest_result['data'] as $task ) {

	// append in known Tasks
	$tasks[] = $task;

	// remember User PHIDs since we will need to get their extra info
	$phid_task_author = $task['fields']['authorPHID'];
	$phid_task_owner  = $task['fields']['ownerPHID'];
	$USERS_BY_PHID[ $phid_task_author ] = null;
	$USERS_BY_PHID[ $phid_task_owner  ] = null;
}

// get users info from their PHID identifiers
$users_phid = array_keys( $USERS_BY_PHID );
$users_api_parameters = [
	'constraints' => [
		'phids' => $users_phid,
	],
];
$users_result = $client->callMethodSynchronous( 'user.search', $users_api_parameters );
foreach( $users_result['data'] as $user_data ) {
	$phid_user = $user_data['phid'];
	$USERS_BY_PHID[ $phid_user ] = $user_data;
}

// for each language
foreach( $I18N as $lang => $msg ) {

	$changelog_blocks = [];

	// for each Task
	foreach( $tasks as $task ) {
		$task_id          = $task['id'];
		$task_name        = $task['fields']['name'];
		$task_descr       = $task['fields']['description'];
		$phid_task_author = $task['fields']['authorPHID'];
		$phid_task_owner  = $task['fields']['ownerPHID'];

		$author = $USERS_BY_PHID[ $phid_task_author ];
		$owner  = $USERS_BY_PHID[ $phid_task_owner  ];

		$username_author = $author['fields']['username'];
		$username_owner  = $owner ['fields']['username'];
		$realname_author = $author['fields']['realName'] ?? null;
		$realname_owner  = $owner ['fields']['realName'] ?? null;

		$task_url   = PHABRICATOR_HOME . "T{$task_id}";
		$url_author = PHABRICATOR_HOME . 'p/' . $username_author;
		$url_owner  = PHABRICATOR_HOME . 'p/' . $username_owner;

		// just try to show something useful for a F-Droid changelog
		$changelog_lines = [];

		// Task name and URL
		$changelog_lines[] = $task_name;

		// reporter by (author)
		$changelog_lines[] = sprintf( $msg['reportedByName'], $username_author );

		// resolved by (owner)
		$changelog_lines[] = sprintf( $msg['resolvedByName'], $username_owner );

		$changelog_lines[] = $task_url;

		$changelog_block = implode( "\n", $changelog_lines );
		$changelog_blocks[] = $changelog_block;
	}

	// print all changelog blocks
	$changelog_content = implode( "\n\n", $changelog_blocks );

	// expected changelog file
	$changelog_path = REPO_PATH . "/metadata/{$lang}/changelogs/{$version_code}.txt";

	// save
	file_put_contents( $changelog_path, $changelog_content );
}
