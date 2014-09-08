/*******************************************************************************
 *   Copyright 2014 Analog Devices, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 ********************************************************************************/

package com.analog.lyric.dimple.solvers.optimizedupdate;

/**
 * Choices for the factor update algorithm.
 * 
 * @since 0.07
 * @author jking
 */
public enum UpdateApproach
{
	/**
	 * Use the normal update algorithm.
	 */
	UPDATE_APPROACH_NORMAL,

	/**
	 * Use the optimized update algorithm.
	 */
	UPDATE_APPROACH_OPTIMIZED,

	/**
	 * Choose which algorithm to use by comparing estimates of their memory usage and execution
	 * time.
	 */
	UPDATE_APPROACH_AUTOMATIC;
}