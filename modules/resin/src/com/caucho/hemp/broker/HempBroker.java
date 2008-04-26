/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.hemp.broker;

import com.caucho.hmtp.spi.HmtpServiceManager;
import com.caucho.hmtp.spi.HmtpBroker;
import com.caucho.hmtp.HmtpConnection;
import com.caucho.hmtp.HmtpError;
import com.caucho.hemp.*;
import com.caucho.hmtp.HmtpAgentStream;
import com.caucho.hmtp.spi.HmtpService;
import com.caucho.hmtp.HmtpStream;
import com.caucho.server.resin.*;
import com.caucho.util.*;
import java.util.*;
import java.util.logging.*;
import java.lang.ref.*;
import java.io.Serializable;


/**
 * Broker
 */
public class HempBroker implements HmtpBroker
{
  private static final Logger log
    = Logger.getLogger(HempBroker.class.getName());
  private static final L10N L = new L10N(HempBroker.class);
  
  // agents
  private final HashMap<String,WeakReference<HmtpAgentStream>> _agentMap
    = new HashMap<String,WeakReference<HmtpAgentStream>>();
  
  private final HashMap<String,WeakReference<HmtpService>> _resourceMap
    = new HashMap<String,WeakReference<HmtpService>>();
  
  private String _serverId = Resin.getCurrent().getServerId();

  private String _domain = "localhost";
  private String _managerJid = "localhost";

  private HmtpServiceManager []_resourceManagerList = new HmtpServiceManager[0];

  //
  // configuration
  //

  /**
   * Adds a broker implementation, e.g. the IM broker.
   */
  public void addBroker(HmtpServiceManager resourceManager)
  {
    addResourceManager(resourceManager);
  }
  
  /**
   * Adds a broker implementation, e.g. the IM broker.
   */
  public void addResourceManager(HmtpServiceManager resourceManager)
  {
    HmtpServiceManager []resourceManagerList = new HmtpServiceManager[_resourceManagerList.length + 1];
    System.arraycopy(_resourceManagerList, 0, resourceManagerList, 0, _resourceManagerList.length);
    resourceManagerList[resourceManagerList.length - 1] = resourceManager;
    _resourceManagerList = resourceManagerList;
    
    resourceManager.setBroker(this);
  }

  //
  // API
  //

  /**
   * Creates a session
   */
  public HmtpConnection getConnection(String uid, String password)
  {
    String jid = generateJid(uid);

    HempConnectionImpl conn = new HempConnectionImpl(this, jid);

    HmtpAgentStream agentStream = conn.getAgentStreamHandler();
      
    synchronized (_agentMap) {
      _agentMap.put(jid, new WeakReference<HmtpAgentStream>(agentStream));
    }

    if (log.isLoggable(Level.FINE))
      log.fine(conn + " created");

    int p = jid.indexOf('/');
    if (p > 0) {
      String owner = jid.substring(0, p);
      
      HmtpService resource = getService(owner);

      if (resource != null)
	resource.onLogin(jid);
    }

    return conn;
  }

  protected String generateJid(String uid)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(uid);
    sb.append("/");
    sb.append(_serverId);
    sb.append(":");

    Base64.encode(sb, RandomUtil.getRandomLong());
    
    return sb.toString();
  }
  
  /**
   * Registers a resource
   */
  public HmtpConnection registerResource(String name, HmtpService resource)
  {
    String jid;
    
    int p;
    if ((p = name.indexOf('/')) > 0) {
      jid = name;
    }
    else if ((p = name.indexOf('@')) > 0) {
      jid = name;
    }
    else {
      jid = name + "@" + getDomain();
    }

    HempConnectionImpl conn = new HempConnectionImpl(this, jid);

    synchronized (_resourceMap) {
      WeakReference<HmtpService> oldRef = _resourceMap.get(jid);

      if (oldRef != null && oldRef.get() != null)
	throw new IllegalStateException(L.l("duplicated jid='{0}' is not allowed",
					    jid));
      
      _resourceMap.put(jid, new WeakReference<HmtpService>(resource));
    }
    
    synchronized (_agentMap) {
      WeakReference<HmtpAgentStream> oldRef = _agentMap.get(jid);

      if (oldRef != null && oldRef.get() != null)
	throw new IllegalStateException(L.l("duplicated jid='{0}' is not allowed",
					    jid));

      HmtpAgentStream agentStream = resource.getAgentStream();
      _agentMap.put(jid, new WeakReference<HmtpAgentStream>(agentStream));
    }

    if (log.isLoggable(Level.FINE))
      log.fine(this + " register jid=" + jid + " " + resource);

    return conn;
  }

  /**
   * Returns the manager's own id.
   */
  protected String getManagerJid()
  {
    return _managerJid;
  }

  /**
   * Returns the domain
   */
  protected String getDomain()
  {
    return _domain;
  }

  /**
   * Presence
   */
  public void sendPresence(String to, String from, Serializable []data)
  {
    /*
    if (to == null) {
      HmtpServiceManager []resourceManagers = _resourceManagerList;

      for (HmtpServiceManager manager : resourceManagers) {
        manager.sendPresence(to, from, data);
      }
    }
    else {
    */
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresence(to, from, data);
    else {
      if (log.isLoggable(Level.FINER)) {
	log.finer(this + " sendPresence (no resource) to=" + to
		  + " from=" + from);
      }
    }
  }

  /**
   * Presence unavailable
   */
  public void sendPresenceUnavailable(String to,
				      String from,
				      Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceUnavailable(to, from, data);
  }

  /**
   * Presence probe
   */
  public void sendPresenceProbe(String to,
			        String from,
			        Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceProbe(to, from, data);
  }

  /**
   * Presence subscribe
   */
  public void sendPresenceSubscribe(String to,
				    String from,
				    Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceSubscribe(to, from, data);
  }

  /**
   * Presence subscribed
   */
  public void sendPresenceSubscribed(String to,
				     String from,
				     Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceSubscribed(to, from, data);
  }

  /**
   * Presence unsubscribe
   */
  public void sendPresenceUnsubscribe(String to,
				      String from,
				      Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceUnsubscribe(to, from, data);
  }

  /**
   * Presence unsubscribed
   */
  public void sendPresenceUnsubscribed(String to,
				       String from,
				       Serializable []data)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceUnsubscribed(to, from, data);
  }

  /**
   * Presence error
   */
  public void sendPresenceError(String to,
			        String from,
			        Serializable []data,
			        HmtpError error)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendPresenceError(to, from, data, error);
  }

  /**
   * Sends a message
   */
  public void sendMessage(String to, String from, Serializable value)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendMessage(to, from, value);
    else {
      log.fine(this + " sendMessage to=" + to + " from=" + from
	       + " is an unknown stream");
    }
  }

  /**
   * Sends a message
   */
  public void sendMessageError(String to,
			       String from,
			       Serializable value,
			       HmtpError error)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendMessageError(to, from, value, error);
    else {
      log.fine(this + " sendMessageError to=" + to + " from=" + from
	       + " error=" + error + " is an unknown stream");
    }
  }

  /**
   * Query an entity
   */
  public boolean sendQueryGet(long id, String to, String from, Serializable query)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null) {
      if (! stream.sendQueryGet(id, to, from, query)) {
	if (log.isLoggable(Level.FINE)) {
	  log.fine(this + " queryGet to unknown feature to='" + to
		   + "' from=" + from + " query='" + query + "'");
	}
	
	String msg = L.l("'{0}' is an unknown feature for to='{1}'",
			 query, to);
    
	HmtpError error = new HmtpError(HmtpError.TYPE_CANCEL,
					HmtpError.FEATURE_NOT_IMPLEMENTED,
					msg);
	
	sendQueryError(id, from, to, query, error);
      }

      return true;
    }

    if (log.isLoggable(Level.FINE)) {
      log.fine(this + " queryGet to unknown stream to='" + to
	       + "' from=" + from);
    }

    String msg = L.l("'{0}' is an unknown service for queryGet", to);
    
    HmtpError error = new HmtpError(HmtpError.TYPE_CANCEL,
				    HmtpError.SERVICE_UNAVAILABLE,
				    msg);
				    
    sendQueryError(id, from, to, query, error);
    
    return true;
  }

  /**
   * Query an entity
   */
  public boolean sendQuerySet(long id, String to, String from, Serializable query)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream == null) {
      if (log.isLoggable(Level.FINE)) {
	log.fine(this + " querySet to unknown stream '" + to
		 + "' from=" + from);
      }

      String msg = L.l("'{0}' is an unknown service for querySet", to);
    
      HmtpError error = new HmtpError(HmtpError.TYPE_CANCEL,
				      HmtpError.SERVICE_UNAVAILABLE,
				      msg);
				    
      sendQueryError(id, from, to, query, error);

      return true;
    }

    if (stream.sendQuerySet(id, to, from, query))
      return true;

    if (log.isLoggable(Level.FINE)) {
      log.fine(this + " querySet with unknown feature to=" + to
	       + " from=" + from + " resource=" + stream
	       + " query=" + query);
    }

    String msg = L.l("'{0}' is an unknown feature for querySet",
		     query);
    
    HmtpError error = new HmtpError(HmtpError.TYPE_CANCEL,
				    HmtpError.FEATURE_NOT_IMPLEMENTED,
				    msg);
				    
    sendQueryError(id, from, to, query, error);

    return true;
  }

  /**
   * Query an entity
   */
  public void sendQueryResult(long id, String to, String from, Serializable value)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendQueryResult(id, to, from, value);
    else
      throw new RuntimeException(L.l("{0} is an unknown entity", to));
  }

  /**
   * Query an entity
   */
  public void sendQueryError(long id,
			 String to,
			 String from,
			 Serializable query,
			 HmtpError error)
  {
    HmtpStream stream = getAgentStream(to);

    if (stream != null)
      stream.sendQueryError(id, to, from, query, error);
    else
      throw new RuntimeException(L.l("{0} is an unknown entity", to));
  }

  protected HmtpAgentStream getAgentStream(String jid)
  {
    synchronized (_agentMap) {
      WeakReference<HmtpAgentStream> ref = _agentMap.get(jid);

      if (ref != null)
	return ref.get();
    }

    HmtpService resource = getService(jid);

    if (resource != null) {
      synchronized (_agentMap) {
	WeakReference<HmtpAgentStream> ref = _agentMap.get(jid);

	if (ref != null)
	  return ref.get();

	HmtpAgentStream agentStream = resource.getAgentStream();
	_agentMap.put(jid, new WeakReference<HmtpAgentStream>(agentStream));

	return agentStream;
      }
    }
    else
      return null;
  }

  protected HmtpService getService(String jid)
  {
    if (jid == null)
      return null;
    
    synchronized (_resourceMap) {
      WeakReference<HmtpService> ref = _resourceMap.get(jid);

      if (ref != null)
	return ref.get();
    }

    HmtpService resource = lookupResource(jid);

    if (resource == null) {
      int p;

      if ((p = jid.indexOf('/')) > 0) {
	String uid = jid.substring(0, p);
	HmtpService user = getService(uid);

	if (user != null)
	  resource = user.lookupResource(jid);
      }
      else if ((p = jid.indexOf('@')) > 0) {
	String domainName = jid.substring(p + 1);
	HmtpService domain = getService(domainName);

	if (domain != null)
	  resource = domain.lookupResource(jid);
      }
    }

    if (resource != null) {
      synchronized (_resourceMap) {
	WeakReference<HmtpService> ref = _resourceMap.get(jid);

	if (ref != null)
	  return ref.get();

	_resourceMap.put(jid, new WeakReference<HmtpService>(resource));

	return resource;
      }
    }
    else
      return null;
  }

  protected HmtpService lookupResource(String jid)
  {
    for (HmtpServiceManager manager : _resourceManagerList) {
      HmtpService resource = manager.lookupResource(jid);

      if (resource != null)
	return resource;
    }

    return null;
  }
  
  /**
   * Closes a connection
   */
  void close(String jid)
  {
    int p = jid.indexOf('/');
    if (p > 0) {
      String owner = jid.substring(0, p);
      
      HmtpService resource = getService(owner);

      if (resource != null) {
	try {
	  resource.onLogout(jid);
	} catch (Exception e) {
	  log.log(Level.FINE, e.toString(), e);
	}
      }
    }
    
    synchronized (_resourceMap) {
      _resourceMap.remove(jid);
    }
    
    synchronized (_agentMap) {
      _agentMap.remove(jid);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
