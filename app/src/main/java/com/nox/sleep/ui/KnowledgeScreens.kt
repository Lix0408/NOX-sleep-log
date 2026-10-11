package com.nox.sleep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nox.sleep.core.NoxColor
import com.nox.sleep.core.SleepGuideline
import com.nox.sleep.ui.components.GroupLabel
import com.nox.sleep.ui.components.Label
import com.nox.sleep.ui.components.MutedText
import com.nox.sleep.ui.components.NoxCard
import com.nox.sleep.ui.components.NoxTopBar
import com.nox.sleep.ui.components.SettingRow

/* ================================================================ 知识屏 */

/**
 * 「为什么是 7–9 小时」知识屏 —— 全 App 唯一允许长文本的页面。
 * 开头先声明立场（不给打分），再给区间、依据与边界。
 */
@Composable
fun GuidelineScreen(
    onBack: () -> Unit,
    onOpenSources: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            NoxTopBar(title = "为什么是 7–9 小时", onBack = onBack)

            Spacer(Modifier.height(10.dp))
            MutedText(
                "NOX 只记录时长，不做评价。这一页给你一条来自权威机构的基准 —— 它不给你打分，只告诉你：健康的睡眠时长，大致落在哪。",
                size = 13.sp
            )

            Spacer(Modifier.height(14.dp))
            NoxCard {
                Label("一个区间，不是一个点")
                Spacer(Modifier.height(6.dp))
                MutedText("WHO 推荐的成人睡眠时长是 7–9 小时。7 小时和 9 小时同等健康 —— 它不是「瞄准 8 小时」，而是「落在 7 到 9 之间都很好」。")
                Spacer(Modifier.height(12.dp))

                // 平顶区间示意：7–9h 是一段等高，两端才回落
                Box(Modifier.fillMaxWidth().height(34.dp)) {
                    val bandStart = SleepGuideline.bandStartFraction
                    val bandEnd = SleepGuideline.bandEndFraction
                    Canvas(Modifier.fillMaxSize()) {
                        val bw = size.width
                        val mid = size.height / 2f
                        val bandH = 12.dp.toPx()
                        drawRoundRect(
                            color = NoxColor.Mist.copy(alpha = 0.18f),
                            topLeft = Offset(bw * bandStart, mid - bandH / 2f),
                            size = Size(bw * (bandEnd - bandStart), bandH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("5h", fontSize = 9.sp, color = NoxColor.Ink3)
                    Text("7h", fontSize = 9.sp, color = NoxColor.MistInk)
                    Text("9h", fontSize = 9.sp, color = NoxColor.MistInk)
                    Text("11h", fontSize = 9.sp, color = NoxColor.Ink3)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "判断自己是否睡够，更实用的标准是：第二天是否精力充沛、日间是否困倦 —— 而不是盯着一个数字。",
                    fontSize = 11.sp, color = NoxColor.Ink3, lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            NoxCard {
                Label("不同年龄，需求不同")
                Spacer(Modifier.height(8.dp))
                Text("青少年 14–17 岁 · 8–10 小时", fontSize = 12.sp, color = NoxColor.Ink2)
                Spacer(Modifier.height(5.dp))
                Text(
                    "成人 18–64 岁 · 7–9 小时",
                    fontSize = 12.sp, color = NoxColor.MistInk, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text("老年人 65+ 岁 · 7–8 小时", fontSize = 12.sp, color = NoxColor.Ink2)
                Spacer(Modifier.height(8.dp))
                Text("NSF 2015 睡眠时长推荐（全年龄段 9 档，此处节选）", fontSize = 10.sp, color = NoxColor.Ink3)
            }

            Spacer(Modifier.height(12.dp))
            NoxCard {
                Label("不足 7 小时，关联什么")
                Spacer(Modifier.height(6.dp))
                MutedText("共识声明指出，规律性睡眠不足 7 小时，与体重增加、肥胖、糖尿病、高血压、心脏病、卒中、抑郁、免疫功能受损等结局相关。")
                Spacer(Modifier.height(6.dp))
                Text(
                    "注意是「相关」，不是「导致」—— 睡眠不足常常伴随其他不健康的生活方式，两者不易拆开。",
                    fontSize = 11.sp, color = NoxColor.Ink3, lineHeight = 16.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("AASM / SRS 2015 联合共识 · 覆盖 9 大健康领域", fontSize = 10.sp, color = NoxColor.Ink3)
            }

            Spacer(Modifier.height(12.dp))
            NoxCard {
                Label("超过 9 小时，怎么说")
                Spacer(Modifier.height(6.dp))
                MutedText("共识的表述很克制：对年轻人、正在偿还睡眠债的人、患病的人，超过 9 小时可能是合适的；对其他人的影响尚不明确。")
                Spacer(Modifier.height(6.dp))
                Text(
                    "长睡不是需要「警告」的坏东西，而是一个「值得留意、但别急着下结论」的状态。",
                    fontSize = 11.sp, color = NoxColor.Ink3, lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            NoxCard {
                Label("这条基准不能告诉你什么")
                Spacer(Modifier.height(6.dp))
                Text("· 它是群体共识，不能推算你个人的健康风险。", fontSize = 12.sp, color = NoxColor.Ink2)
                Spacer(Modifier.height(4.dp))
                Text("· 睡眠健康包括时长、质量、规律性等六个维度 —— NOX 只记录时长，这条基准只对照时长。", fontSize = 12.sp, color = NoxColor.Ink2)
                Spacer(Modifier.height(4.dp))
                Text("· 以上为公开推荐整理，不构成医疗建议。", fontSize = 12.sp, color = NoxColor.Ink2)
            }

            Spacer(Modifier.height(12.dp))
            SettingRow(
                "数据来源与引用",
                value = "WHO · NSF · AASM",
                showChevron = true,
                onClick = onOpenSources
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ================================================================ 来源页 */

/** 数据来源页：如实说明「WHO 推荐」与 NSF / AASM 共识之间的引用关系。 */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            NoxTopBar(title = "数据来源", onBack = onBack)

            Spacer(Modifier.height(10.dp))
            MutedText(
                "「成人 7–9 小时」在中文媒体广泛称为「WHO 建议」，其循证出处如下：",
                size = 13.sp
            )

            Spacer(Modifier.height(14.dp))
            GroupLabel("NSF · 2015（国家睡眠基金会）")
            NoxCard {
                Text(
                    "Hirshkowitz, M., Whiton, K., et al. (2015). National Sleep Foundation's sleep time duration recommendations. Sleep Health, 1(1), 40–43.",
                    fontSize = 12.sp, color = NoxColor.Ink2, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "成人 26–64 岁 7–9 小时；65 岁以上 7–8 小时。2026 年经 133 项荟萃分析复审，结论不变。",
                    fontSize = 11.sp, color = NoxColor.Ink3, lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            GroupLabel("AASM / SRS · 2015（美国睡眠医学会）")
            NoxCard {
                Text(
                    "Watson, N. F., et al. (2015). Recommended amount of sleep for a healthy adult. SLEEP, 38(6), 843–844.",
                    fontSize = 12.sp, color = NoxColor.Ink2, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "成人应规律睡眠 ≥7 小时；不足 7 小时与多种不良健康结局相关。",
                    fontSize = 11.sp, color = NoxColor.Ink3, lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            NoxCard {
                Text(
                    "以上内容为对公开健康推荐与文献的整理，用于提供群体背景信息，不构成任何医疗建议或个体诊断。如需睡眠相关帮助，请咨询专业医疗机构。",
                    fontSize = 12.sp, color = NoxColor.Ink2, lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
