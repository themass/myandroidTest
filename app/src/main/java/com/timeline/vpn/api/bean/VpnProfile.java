/*
 * Copyright (C) 2012-2015 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package com.timeline.vpn.api.bean;


import com.timeline.vpn.service.utils.VpnType;

import java.io.Serializable;

public class VpnProfile implements Cloneable,Serializable
{
	/* While storing this as EnumSet would be nicer this simplifies storing it in a database */
	public static final int SPLIT_TUNNELING_BLOCK_IPV4 = 1;
	public static final int SPLIT_TUNNELING_BLOCK_IPV6 = 2;

	private String mName, mGateway, mUsername, mPassword, mCertificate, mUserCertificate;
	private Integer mMTU, mPort, mSplitTunneling;
	private VpnType mVpnType;
	private long expire;
	private long mId = -1;

	public long getId()
	{
		return mId;
	}

	public void setId(long id)
	{
		this.mId = id;
	}

	public String getName()
	{
		return mName;
	}

	public void setName(String name)
	{
		this.mName = name;
	}

	public String getGateway()
	{
		return mGateway;
	}

	public void setGateway(String gateway)
	{
		this.mGateway = gateway;
	}

	public VpnType getVpnType()
	{
		return mVpnType;
	}

	public void setVpnType(VpnType type)
	{
		this.mVpnType = type;
	}

	public String getUsername()
	{
		return mUsername;
	}

	public void setUsername(String username)
	{
		this.mUsername = username;
	}

	public String getPassword()
	{
		return mPassword;
	}

	public void setPassword(String password)
	{
		this.mPassword = password;
	}

	public String getCert()
	{
		return mCertificate;
	}

	public void setCert(String alias)
	{
		this.mCertificate = alias;
	}

	public String getUserCertificateAlias()
	{
		return mUserCertificate;
	}

	public void setUserCertificateAlias(String alias)
	{
		this.mUserCertificate = alias;
	}

	public String getMTU()
	{
		return null;
	}

	public void setMTU(Integer mtu)
	{
		this.mMTU = mtu;
	}

	public String getPort()
	{
		return String.valueOf(mPort);
	}

	public void setPort(Integer port)
	{
		this.mPort = port;
	}

	public Integer getSplitTunneling()
	{
		return mSplitTunneling;
	}

	public void setSplitTunneling(Integer splitTunneling)
	{
		this.mSplitTunneling = splitTunneling;
	}

	public long getExpire() {
		return expire;
	}

	public void setExpire(long expire) {
		this.expire = expire;
	}

	@Override
	public String toString()
	{
		return mName;
	}

	@Override
	public boolean equals(Object o)
	{
		if (o != null && o instanceof VpnProfile)
		{
			return this.mId == ((VpnProfile)o).getId();
		}
		return false;
	}

	@Override
	public VpnProfile clone()
	{
		try
		{
			return (VpnProfile)super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			throw new AssertionError();
		}
	}
}
