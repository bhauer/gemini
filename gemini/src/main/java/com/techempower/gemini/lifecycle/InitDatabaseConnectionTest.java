/*******************************************************************************
 * Copyright (c) 2018, TechEmpower, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name TechEmpower, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL TECHEMPOWER, INC. BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package com.techempower.gemini.lifecycle;

import com.techempower.data.*;
import com.techempower.gemini.*;
import com.techempower.helper.*;
import com.techempower.log.*;
import com.techempower.util.*;

/**
 * Verifies connectivity to the database before allowing the application to
 * start.
 */
public class InitDatabaseConnectionTest
  implements InitializationTask,
             Configurable
{

  // Initialization test.
  private String testQuery  = "SELECT 1 AS Result;";
  private String testColumn = "Result";
  private String testValue  = "1";
  
  /**
   * Constructor.
   */
  public InitDatabaseConnectionTest(GeminiApplication app)
  {
    app.getConfigurator().addConfigurable(this);
  }

  @Override
  public void taskInitialize(GeminiApplication app)
  {
    final ComponentLog log = app.getLog(COMPONENT_CODE);
    final ConnectorFactory cf = app.getConnectorFactory();
    
    // Only proceed if we've got a test query specified and the application's
    // connector factory is enabled.
    if (  (cf.isEnabled())
       && (StringHelper.isNonEmpty(testQuery))
       )
    {
      log.log("Testing database connectivity.");
      log.log("Running test query: " + testQuery);
      try (
          DatabaseConnector dbConn = cf.getConnector(testQuery)
          )
      {
        dbConn.setForceNewConnection(true);
        dbConn.runQuery();
        if (dbConn.more())
        {
          final String returnValue = dbConn.getField(testColumn, "");
          if (testValue.equals(returnValue))
          {
            log.log("Successfully communicated with database.");
            cf.determineIdentifierQuoteString();
            return;
          }
          else
          {
            throw new GeminiInitializationError("Return value mismatch. " 
                + "Received " + returnValue + "; "
                + "expected " + testValue + ".");
          }
        }
        else
        {
          throw new GeminiInitializationError("No results from query.");
        }
      }
      catch (Exception exc)
      {
        throw new GeminiInitializationError("Unable to verify database connectivity", exc);
      }
    }
    else
    {
      log.log("Skipping database connectivity test.");
    }
  }

  @Override
  public void configure(EnhancedProperties props) 
  {
    final EnhancedProperties.Focus focus = props.focus("Initialization.");
    
    // Read the initialization evaluation query.
    testQuery  = focus.get("TestQuery", testQuery);
    testColumn = focus.get("TestColumn", testColumn);
    testValue  = focus.get("TestValue", testValue);
  }

}
