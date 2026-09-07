package app.gyrolet.mpvrx.repository.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class AiPromptsBilingualTest {

  @Test
  fun testBilingualInstructionContainsRules() {
    val prompt = AiPrompts.BILINGUAL_SUBTITLE_TRANSLATION_INSTRUCTION
    assertTrue(prompt.contains("bilingual", ignoreCase = true))
    assertTrue(prompt.contains("original", ignoreCase = true))
    assertTrue(prompt.contains("translation", ignoreCase = true))
  }
}
