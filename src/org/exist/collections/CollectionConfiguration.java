/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.exist.collections.triggers.Trigger;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CollectionConfiguration {

    public final static String COLLECTION_CONFIG_SUFFIX = ".xconf"; 
    public final static String DEFAULT_COLLECTION_CONFIG_FILE = "collection" + COLLECTION_CONFIG_SUFFIX; 
    	
    public final static String NAMESPACE = "http://exist-db.org/collection-config/1.0";
    
	private final static String ROOT_ELEMENT = "collection";
	private final static String TRIGGERS_ELEMENT = "triggers";
	private final static String EVENT_ATTRIBUTE = "event";
	private final static String CLASS_ATTRIBUTE = "class";
	private final static String PARAMETER_ELEMENT = "parameter";
	private final static String PARAM_NAME_ATTRIBUTE = "name";
	private final static String PARAM_VALUE_ATTRIBUTE = "value";
	private final static String INDEX_ELEMENT = "index";
	private final static String PERMISSIONS_ELEMENT = "default-permissions";
	private final static String RESOURCE_PERMISSIONS_ATTR = "resource";
	private final static String COLLECTION_PERMISSIONS_ATTR = "collection";
	
	private static final Logger LOG = Logger.getLogger(CollectionConfiguration.class);

	private Trigger[] triggers = new Trigger[6];

	private IndexSpec indexSpec = null;

	private Collection collection;
	
	private int defCollPermissions;
	private int defResPermissions;
	
    public CollectionConfiguration(BrokerPool pool, Collection collection) {
    	this.collection = collection;
    	this.defResPermissions = pool.getSecurityManager().getResourceDefaultPerms();
		this.defCollPermissions = pool.getSecurityManager().getCollectionDefaultPerms();
    }
	
	/**
     * @param broker
     * @param collection
     * @param doc
     * @throws CollectionConfigurationException
     */
    protected void read(DBBroker broker, Document doc) throws CollectionConfigurationException {
        Element root = doc.getDocumentElement();
        if (root == null)
            throw new CollectionConfigurationException("Configuration document can not be parsed"); 
        if (!ROOT_ELEMENT.equals(root.getLocalName()))
            throw new CollectionConfigurationException("Expected element '" + ROOT_ELEMENT +
                    "' in configuration document. Got element '" + root.getLocalName() + "'");               
		if(!NAMESPACE.equals(root.getNamespaceURI()))
            throw new CollectionConfigurationException("Expected namespace '" + NAMESPACE +                            
                    "' for element '" + PARAMETER_ELEMENT + 
                    "' in configuration document. Got '" + root.getNamespaceURI() + "'");     

        NodeList childNodes = root.getChildNodes();
		Node node;
		for(int i = 0; i < childNodes.getLength(); i++) {
			node = childNodes.item(i);
			if(NAMESPACE.equals(node.getNamespaceURI())) {
			    if(TRIGGERS_ELEMENT.equals(node.getLocalName())) {
					NodeList triggers = node.getChildNodes();
					for(int j = 0; j < triggers.getLength(); j++) {
						node = triggers.item(j);
						if(node.getNodeType() == Node.ELEMENT_NODE)
							createTrigger(broker, (Element)node);
					}
			    } else if(INDEX_ELEMENT.equals(node.getLocalName())) {
			        Element elem = (Element) node;
                    try {
                        if(indexSpec == null)
                            indexSpec = new IndexSpec(elem);
                        else
                            indexSpec.read(elem);
                    } catch (DatabaseConfigurationException e) {
                        throw new CollectionConfigurationException(e.getMessage(), e);
                    }
			    } else if (PERMISSIONS_ELEMENT.equals(node.getLocalName())) {
			    	Element elem = (Element) node;
			    	String permsOpt = elem.getAttribute(RESOURCE_PERMISSIONS_ATTR);
					if (permsOpt != null && permsOpt.length() > 0) {
						LOG.debug("RESOURCE: " + permsOpt);
						try {
							defResPermissions = Integer.parseInt(permsOpt, 8);
						} catch (NumberFormatException e) {
							throw new CollectionConfigurationException("Ilegal value for permissions in configuration document : " +                                    
								e.getMessage(), e);
						}
					}
					permsOpt = elem.getAttribute(COLLECTION_PERMISSIONS_ATTR);
					if (permsOpt != null && permsOpt.length() > 0) {
						LOG.debug("COLLECTION: " + permsOpt);
						try {
							defCollPermissions = Integer.parseInt(permsOpt, 8);
						} catch (NumberFormatException e) {
							throw new CollectionConfigurationException("Ilegal value for permissions in configuration document : " +                                     
								e.getMessage(), e);
						}
					}
			    } else {
                    LOG.info("Ignored node '" + node.getLocalName() + "' in configuration document");
                    //TODO : throw an exception like above ? -pb
                }
			} else {
                LOG.info("Ignored node '" + node.getLocalName() + "' in namespace '" + 
                        node.getNamespaceURI() + "' in configuration document");                
            }
		}
    }

    protected void setIndexConfiguration(IndexSpec spec) {
        this.indexSpec = spec;
    }
    
    public Collection getCollection() {
    	return collection;
    }
    
    public int getDefCollPermissions() {
    	return defCollPermissions;
    }
    
    public int getDefResPermissions() {
    	return defResPermissions;
    }
    
    public IndexSpec getIndexConfiguration() {
        return indexSpec;
    }
	
	public Trigger getTrigger(int eventType) {
		return triggers[eventType];
	}
	
	private void createTrigger(DBBroker broker, Element node) 
            throws CollectionConfigurationException {
        
		String eventAttr = node.getAttribute(EVENT_ATTRIBUTE);
		if(eventAttr == null)
			throw new CollectionConfigurationException("'" + node.getNodeName() + 
                    "' requires an attribute '"+ EVENT_ATTRIBUTE + "'");
		String classAttr = node.getAttribute(CLASS_ATTRIBUTE);
		if(classAttr == null)
			throw new CollectionConfigurationException("'" + node.getNodeName() + 
                    "' requires an attribute '"+ CLASS_ATTRIBUTE + "'"); 
        
        Trigger trigger = instantiate(broker, node, classAttr);
        
        StringTokenizer tok = new StringTokenizer(eventAttr, ", ");
        String event;             
		while(tok.hasMoreTokens()) {
			event = tok.nextToken();
            LOG.debug("Registering trigger '" + classAttr + "' for event '" + event + "'");
			if(event.equalsIgnoreCase("store")) {
                if (triggers[Trigger.STORE_DOCUMENT_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                    
                triggers[Trigger.STORE_DOCUMENT_EVENT] = trigger;
			} else if(event.equalsIgnoreCase("update")) {
                if (triggers[Trigger.UPDATE_DOCUMENT_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                     
				triggers[Trigger.UPDATE_DOCUMENT_EVENT] = trigger;
			} else if(event.equalsIgnoreCase("remove")) {
                if (triggers[Trigger.REMOVE_DOCUMENT_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                     
				triggers[Trigger.REMOVE_DOCUMENT_EVENT] = trigger;
			} else if(event.equalsIgnoreCase("create-collection")) {
                if (triggers[Trigger.CREATE_COLLECTION_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                     
				triggers[Trigger.CREATE_COLLECTION_EVENT] = trigger;
			} else if(event.equalsIgnoreCase("rename-collection")) {
                if (triggers[Trigger.RENAME_COLLECTION_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                     
				triggers[Trigger.RENAME_COLLECTION_EVENT] = trigger;
			} else if(event.equalsIgnoreCase("delete-collection")) {
                if (triggers[Trigger.DELETE_COLLECTION_EVENT] != null)
                    throw new CollectionConfigurationException("Trigger '" + classAttr + "' already registered");                     
				triggers[Trigger.DELETE_COLLECTION_EVENT] = trigger;
			} else
                throw new CollectionConfigurationException("Unknown event type '" + event + 
                        "' in trigger '" + classAttr + "'");
		}
	}
	
	private Trigger instantiate(DBBroker broker, Element node, String classname) 
            throws CollectionConfigurationException {
		try {
			Class clazz = Class.forName(classname);
			if(!Trigger.class.isAssignableFrom(clazz))
				throw new CollectionConfigurationException("Trigger's class '" + classname + 
                        "' is not assignable from '" + Trigger.class + "'");			
            
			NodeList nodes = node.getElementsByTagNameNS(NAMESPACE, PARAMETER_ELEMENT);
            //TODO : rely on schema-driven validation -pb
            if (nodes.getLength() == 0)
                throw new CollectionConfigurationException("Expected '" + PARAM_NAME_ATTRIBUTE +
                    "' elements in namespace '" + NAMESPACE + "' in trigger's configuration.");             
     
            Map parameters = new HashMap(5); 
            for (int i = 0 ; i < nodes.getLength();  i++) {
                Element param = (Element)nodes.item(i);
                //TODO : rely on schema-driven validation -pb
                String name = param.getAttribute(PARAM_NAME_ATTRIBUTE);
                if(name == null)
                    throw new CollectionConfigurationException("Expected attribute '" + PARAM_NAME_ATTRIBUTE +
                            "' for element '" + PARAMETER_ELEMENT + "' in trigger's configuration."); 
                String value = param.getAttribute(PARAM_VALUE_ATTRIBUTE);
                if(value == null)
                    throw new CollectionConfigurationException("Expected attribute '" + PARAM_NAME_ATTRIBUTE +
                            "' for element '" + PARAMETER_ELEMENT + "' in trigger's configuration."); 
                
                parameters.put(name, value);                        
            }     
			
            Trigger trigger = (Trigger)clazz.newInstance();
			trigger.configure(broker, collection, parameters);
            return trigger;
            
		} catch (ClassNotFoundException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		} catch (InstantiationException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		}
	}
	
	public String toString() {
		StringBuffer result = new StringBuffer();
		if (indexSpec != null)
			result.append(indexSpec.toString()).append('\n');		
		for (int i = 0 ; i < triggers.length; i++) {
			Trigger trigger = triggers[i];
			if (trigger != null) {
				switch (i) {
					case Trigger.STORE_DOCUMENT_EVENT : result.append("store document trigger");
					case Trigger.UPDATE_DOCUMENT_EVENT : result.append("update document trigger");
					case Trigger.REMOVE_DOCUMENT_EVENT : result.append("remove document trigger");
					case Trigger.CREATE_COLLECTION_EVENT : result.append("create collection trigger");		
					case Trigger.RENAME_COLLECTION_EVENT : result.append("rename collection trigger");
					case Trigger.DELETE_COLLECTION_EVENT : result.append("delete collection trigger");		
				}			
				result.append('\t').append(trigger.toString()).append('\n');
			}
		}		
		return result.toString();
	}
}
