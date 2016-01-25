package com.gemstone.gemfire.internal.offheap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class FreeListManagerTest {
  static {
    ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
  }

  private final SimpleMemoryAllocatorImpl ma = mock(SimpleMemoryAllocatorImpl.class);
  private final UnsafeMemoryChunk slab = new UnsafeMemoryChunk(1024*1024*5);
  private final OffHeapMemoryStats stats = mock(OffHeapMemoryStats.class);
  private final ChunkFactory cf = new GemFireChunkFactory();
  private FreeListManager freeListManager;
  

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    when(ma.getSlabs()).thenReturn(new UnsafeMemoryChunk[] {slab});
    when(ma.getTotalMemory()).thenReturn((long) slab.getSize());
    when(ma.getStats()).thenReturn(stats);
    when(ma.getChunkFactory()).thenReturn(cf);
    
    this.freeListManager = new FreeListManager(ma);
  }

  @After
  public void tearDown() throws Exception {
    slab.release();
  }

  @Test
  public void usedMemoryIsZeroOnDefault() {
    assertEquals(0, this.freeListManager.getUsedMemory());
  }

  @Test
  public void freeMemoryIsAllocatorTotalOnDefault() {
    assertEquals(ma.getTotalMemory(), this.freeListManager.getFreeMemory());
  }
  
  @Test
  public void allocateTinyChunkHasCorrectSize() {
    int tinySize = 10;
    Chunk c = this.freeListManager.allocate(tinySize, null);
    assertNotNull(c);
    assertEquals(tinySize, c.getDataSize());
    assertEquals(computeExpectedSize(tinySize), c.getSize());
  }

  @Test
  public void allocateTinyChunkFromFreeListHasCorrectSize() {
    int tinySize = 10;
    Chunk c = this.freeListManager.allocate(tinySize, null);
    assertNotNull(c);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    c = this.freeListManager.allocate(tinySize, null);
    assertEquals(tinySize, c.getDataSize());
    assertEquals(computeExpectedSize(tinySize), c.getSize());
  }

  @Test
  public void allocateHugeChunkHasCorrectSize() {
    int hugeSize = FreeListManager.MAX_TINY+1;
    Chunk c = this.freeListManager.allocate(hugeSize, null);
    assertNotNull(c);
    assertEquals(hugeSize, c.getDataSize());
    assertEquals(computeExpectedSize(hugeSize), c.getSize());
  }
  
  @Test
  public void allocateHugeChunkFromFreeListHasCorrectSize() {
    int dataSize = FreeListManager.MAX_TINY+1;
    Chunk c = this.freeListManager.allocate(dataSize, null);
    assertNotNull(c);
    Chunk.release(c.getMemoryAddress(), this.freeListManager);
    c = this.freeListManager.allocate(dataSize, null);
    assertEquals(dataSize, c.getDataSize());
    assertEquals(computeExpectedSize(dataSize), c.getSize());
  }
  
  private int computeExpectedSize(int dataSize) {
    return ((dataSize + Chunk.OFF_HEAP_HEADER_SIZE + 7) / 8) * 8;
  }

  @Test
  public void allocateTinyChunkWithExplicitTypeHasCorrectSize() {
    Chunk c = this.freeListManager.allocate(10, GemFireChunk.TYPE);
    assertNotNull(c);
    assertEquals(10, c.getDataSize());
    assertEquals(24, c.getSize());
  }
  
  @Test(expected = AssertionError.class)
  public void allocateZeroThrowsAssertion() {
    this.freeListManager.allocate(0, null);
  }
  
  @Test(expected = AssertionError.class)
  public void allocateNegativeThrowsAssertion() {
    this.freeListManager.allocate(-123, null);
  }
  
  @Test
  public void hugeMultipleLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyHugeMultiple(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("HUGE_MULTIPLE must be >= 0 and <= " + FreeListManager.HUGE_MULTIPLE + " but it was -1"));
    }
  }
  @Test
  public void hugeMultipleGreaterThan256IsIllegal() {
    try {
      FreeListManager.verifyHugeMultiple(257);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("HUGE_MULTIPLE must be >= 0 and <= 256 but it was 257"));
    }
  }
  @Test
  public void hugeMultipleof256IsLegal() {
    FreeListManager.verifyHugeMultiple(256);
  }
  
  @Test
  public void offHeapFreeListCountLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapFreeListCount(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_FREE_LIST_COUNT must be >= 1."));
    }
  }
  @Test
  public void offHeapFreeListCountOfZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapFreeListCount(0);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_FREE_LIST_COUNT must be >= 1."));
    }
  }
  @Test
  public void offHeapFreeListCountOfOneIsLegal() {
    FreeListManager.verifyOffHeapFreeListCount(1);
  }
  @Test
  public void offHeapAlignmentLessThanZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(-1);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_ALIGNMENT must be a multiple of 8"));
    }
  }
  @Test
  public void offHeapAlignmentNotAMultipleOf8IsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(9);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_ALIGNMENT must be a multiple of 8"));
    }
  }
  @Test
  public void offHeapAlignmentGreaterThan256IsIllegal() {
    try {
      FreeListManager.verifyOffHeapAlignment(256+8);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_ALIGNMENT must be <= 256"));
    }
  }
  @Test
  public void offHeapAlignmentOf256IsLegal() {
    FreeListManager.verifyOffHeapAlignment(256);
  }
  @Test
  public void offHeapBatchAllocationSizeOfZeroIsIllegal() {
    try {
      FreeListManager.verifyOffHeapBatchAllocationSize(0);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertEquals(true, expected.getMessage().contains("gemfire.OFF_HEAP_BATCH_ALLOCATION_SIZE must be >= 1."));
    }
  }
  @Test
  public void offHeapBatchAllocationSizeOfOneIsLegal() {
    FreeListManager.verifyOffHeapBatchAllocationSize(1);
  }
}