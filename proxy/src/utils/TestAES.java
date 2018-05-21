package utils;

import java.math.BigInteger;
import java.util.Random;

import javax.servlet.ServletException;

public class TestAES
{
	public static void main(String[] args) throws ServletException
	{
		Random rand = new Random();
		int count = 0;
		while (true)
		{
			System.out.println(++count);
			for (int bits = 8; bits <= 8000; bits++)
			{
				BigInteger n = new BigInteger(bits, rand);
				String s = Encryption.encrypt(n.toString());
				s = Encryption.decrypt(s);
				if (!n.toString().equals(s))
				{
					System.out.println(s);
					return;
				}
			}
		}
	}
}
