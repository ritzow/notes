package ritzow.notes.server.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestGeneral {

	@Test
	@DisplayName("Print to stdout")
	public void test1() {
		System.out.println("A JUnit test!");
		assertTrue(true);
	}
}
