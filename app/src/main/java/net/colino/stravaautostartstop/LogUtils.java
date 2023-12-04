/*
   Stravomatic - Automatically start Strava when bicycling/running
   Copyright (C) 2018-2023 Colin Leroy-Mira <colin@colino.net>

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.colino.stravaautostartstop;
import android.util.Log;

public class LogUtils {
    public static void d(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.d(tag, message);
        }
    }
    public static void i(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.i(tag, message);
        }
    }
    public static void e(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.e(tag, message);
        }
    }
}
