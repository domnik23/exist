/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBChangeUser extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("change-user", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Change properties of an existing user. Parameters are: username, password, " +
			"group memberships, home collection.",
			new SequenceType[]{
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
                    new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));
	
	public XMLDBChangeUser(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		String userName = args[0].getStringValue();
		Collection collection = null;
		try {
            collection = new LocalCollection(context.getUser(), context.getBroker().getBrokerPool(), DBBroker.ROOT_COLLECTION);
			UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			User oldUser = ums.getUser(userName);
			User user = new User(oldUser.getName());
			if(user == null)
				throw new XPathException(getASTNode(), "User " + userName + " not found");
			if(args[1].getLength() > 0) {
				// set password
				user.setPassword(args[1].getStringValue());
			} else
				user.setPasswordDigest(oldUser.getPassword());
			if(args[2].getLength() > 0) {
				// set groups
				for(SequenceIterator i = args[2].iterate(); i.hasNext(); ) {
					user.addGroup(i.nextItem().getStringValue());
				}
			} else
				user.setGroups(oldUser.getGroups());
			if(args[3].getLength() > 0) {
				// set home collection
				user.setHome(args[3].getStringValue());
			} else
				user.setHome(oldUser.getHome());
			ums.updateUser(user);
		} catch (XMLDBException xe) {
			throw new XPathException(getASTNode(), "Failed to update user " + userName, xe);
        } finally {
            if (null != collection)
                try { collection.close(); } catch (XMLDBException e) { /* ignore */ }
		}
        return Sequence.EMPTY_SEQUENCE;
	}
}
