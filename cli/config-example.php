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

/**
 * The "config-example.php" file must be copied as "config.php" file
 */

// pathname to your Arcanist installation
define( 'ARCANIST_INIT', __DIR__ . '/../../arcanist/support/init/init-script.php' );

// Phabricator Conduit API token
// https://gitpull.it/conduit/token/
define( 'CONDUIT_API_TOKEN', 'asd' );

// must end with a slash
define( 'PHABRICATOR_HOME', 'https://gitpull.it/' );
