package com.infendro.otel.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.addChildren
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class Extension() : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val firstFile = moduleFragment.files[0]

        // region helpers
        val unit = pluginContext.irBuiltIns.unitType
        val any = pluginContext.irBuiltIns.anyType
        val int = pluginContext.irBuiltIns.intType
        val long = pluginContext.irBuiltIns.longType
        val float = pluginContext.irBuiltIns.floatType
        val double = pluginContext.irBuiltIns.doubleType
        val boolean = pluginContext.irBuiltIns.booleanType
        val char = pluginContext.irBuiltIns.charType
        val string = pluginContext.irBuiltIns.stringType

        fun getClass(
            packageName: String,
            name: String
        ): IrClassSymbol {
            val classId = ClassId(FqName(packageName), Name.identifier(name))
            return pluginContext.referenceClass(classId)
                ?: throw Exception("class \"${packageName}/${name}\" not found")
        }

        fun getFunction(
            packageName: String,
            name: String,
            filter: (IrFunction) -> Boolean = { true }
        ): IrSimpleFunctionSymbol {
            val callableId = CallableId(FqName(packageName), Name.identifier(name))
            return pluginContext.referenceFunctions(callableId).singleOrNull { filter(it.owner) }
                ?: throw Exception("function \"${packageName}/${name}\" not found")
        }

        fun IrClassSymbol.type(): IrType {
            return defaultType
        }

        fun IrClassSymbol.getClass(
            name: String
        ): IrClassSymbol {
            return owner.declarations
                .filterIsInstance<IrClass>()
                .single { it.name.toString() == name }
                .symbol
        }

        fun IrClassSymbol.getFunction(
            name: String,
            filter: (IrFunction) -> Boolean = { true }
        ): IrSimpleFunctionSymbol {
            return owner.declarations
                .filterIsInstance<IrSimpleFunction>()
                .single { it.name.toString() == name && filter(it) }
                .symbol
        }

        val fields = mutableListOf<IrField>()
        val functions = mutableListOf<IrFunction>()

        fun buildField(
            name: String,
            type: IrType,
            final: Boolean = false,
            static: Boolean = false,
            block: IrField.() -> Unit = {}
        ): IrField {
            return pluginContext.irFactory
                .buildField {
                    this.name = Name.identifier(name)
                    this.type = type
                    this.isFinal = final
                    this.isStatic = static
                }
                .apply(block)
                .also(fields::add)
        }

        fun buildFunction(
            name: String,
            returnType: IrType = unit,
            block: IrSimpleFunction.() -> Unit = {}
        ): IrSimpleFunction {
            return pluginContext.irFactory
                .buildFun {
                    this.name = Name.identifier(name)
                    this.returnType = returnType
                }
                .apply(block)
                .also(functions::add)
        }

        fun IrFunction.parameter(
            block: IrValueParameterBuilder.() -> Unit
        ) {
            addValueParameter {
                block()
            }
        }

        fun expression(
            block: DeclarationIrBuilder.() -> IrExpression
        ): IrExpressionBody {
            val builder = DeclarationIrBuilder(
                pluginContext,
                firstFile.symbol
            )
            return builder.irExprBody(builder.block())
        }

        fun IrFunction.body(
            block: IrBlockBodyBuilder.() -> Unit
        ) {
            body = DeclarationIrBuilder(
                pluginContext,
                symbol,
                startOffset,
                endOffset
            ).irBlockBody {
                block()
            }
        }

        fun IrBuilderWithScope.call(
            function: IrSimpleFunctionSymbol,
            block: IrCall.() -> Unit = {}
        ): IrCall {
            return irCall(function).apply(block)
        }

        fun IrBuilderWithScope.call(
            function: IrSimpleFunction,
            block: IrCall.() -> Unit = {}
        ) = call(function.symbol, block)

        fun callConstructor(
            constructor: IrConstructorSymbol
        ): IrConstructorCall {
            return DeclarationIrBuilder(
                pluginContext,
                firstFile.symbol
            ).irCallConstructor(
                constructor,
                listOf()
            )
        }

        fun callConstructor(
            constructor: IrConstructor
        ) = callConstructor(constructor.symbol)

        fun IrCall.argument(
            index: Int,
            value: IrExpression?
        ) {
            putValueArgument(index, value)
        }
        // endregion

        // region
        val Instant = getClass(
            "kotlinx.datetime",
            "Instant"
        )

        val Clock = getClass(
            "kotlinx.datetime",
            "Clock"
        )
        val System = Clock.getClass("System")
        val now = System.getFunction("now")

        val Exporter = getClass("com.infendro.otlp", "OtlpExporter")
        val Exporter_constructor = Exporter.constructors.single { it.owner.isPrimary }

        val Processor = getClass(
            "io.opentelemetry.kotlin.sdk.trace.export",
            "BatchSpanProcessor"
        )
        val Processor_shutdown = Processor.getFunction("shutdown")
        val ProcessorCompanion = Processor.getClass("Companion")
        val Processor_builder = ProcessorCompanion.getFunction("builder")

        val ProcessorBuilder = getClass(
            "io.opentelemetry.kotlin.sdk.trace.export",
            "BatchSpanProcessorBuilder"
        )
        val ProcessorBuilder_build = ProcessorBuilder.getFunction("build")

        val TracerProvider = getClass(
            "io.opentelemetry.kotlin.sdk.trace",
            "SdkTracerProvider"
        )
        val TracerProvider_tracerBuilderFunction = TracerProvider.getFunction("tracerBuilder")
        val TracerProviderCompanion = TracerProvider.getClass("Companion")
        val TracerProvider_builder = TracerProviderCompanion.getFunction("builder")

        val TracerProviderBuilder = getClass(
            "io.opentelemetry.kotlin.sdk.trace",
            "SdkTracerProviderBuilder"
        )
        val TracerProviderBuilder_addSpanProcessor = TracerProviderBuilder.getFunction("addSpanProcessor")
        val TracerProviderBuilder_build = TracerProviderBuilder.getFunction("build")

        val TracerBuilder = getClass(
            "io.opentelemetry.kotlin.api.trace",
            "TracerBuilder"
        )
        val TracerBuilder_build = TracerBuilder.getFunction("build")

        val Tracer = getClass("io.opentelemetry.kotlin.api.trace", "Tracer")
        val Tracer_spanBuilder = Tracer.getFunction("spanBuilder")

        val Context = getClass("io.opentelemetry.kotlin.context", "Context")
        val ImplicitContextKeyed = getClass(
            "io.opentelemetry.kotlin.context",
            "ImplicitContextKeyed"
        )
        val Context_with = Context.getFunction("with") {
            it.valueParameters.size == 1 && it.valueParameters[0].type == ImplicitContextKeyed.type()
        }
        val Context_makeCurrent = Context.getFunction("makeCurrent")
        val ContextCompanion = Context.getClass("Companion")
        val Context_current = ContextCompanion.getFunction("current")

        val SpanBuilder = getClass("io.opentelemetry.kotlin.api.trace", "SpanBuilder")
        val SpanBuilder_setParent = SpanBuilder.getFunction("setParent")
        val SpanBuilder_setStartTimestamp = SpanBuilder.getFunction("setStartTimestamp") {
            it.valueParameters.size == 1 && it.valueParameters[0].type == Instant.type()
        }
        val SpanBuilder_startSpan = SpanBuilder.getFunction("startSpan")

        val Span = getClass("io.opentelemetry.kotlin.api.trace", "Span")
        val Span_end = Span.getFunction("end") {
            it.valueParameters.size == 1 && it.valueParameters[0].type == Instant.type()
        }

        val await = getFunction("com.infendro.otel.util", "await")
        // endregion

        // region fields
        // val exporter = OtlpExporter()
        val exporter = buildField(
            name = "_exporter",
            type = Exporter.type(),
            static = true,
        ) {
            initializer = expression {
                callConstructor(Exporter_constructor)
            }
        }

        // val processor = BatchSpanProcessor.builder(exporter).build()
        val processor = buildField(
            name = "_processor",
            type = Processor.type(),
            static = true,
        ) {
            initializer = expression {
                call(ProcessorBuilder_build) {
                    dispatchReceiver = call(Processor_builder) {
                        dispatchReceiver = irGetObject(ProcessorCompanion)
                        argument(0, irGetField(null, exporter))
                    }
                }
            }
        }

        // val provider = SdkTracerProvider.builder().addSpanProcessor(processor).build()
        val provider = buildField(
            name = "_provider",
            type = TracerProvider.type(),
            static = true
        ) {
            initializer = expression {
                call(TracerProviderBuilder_build) {
                    dispatchReceiver = call(TracerProviderBuilder_addSpanProcessor) {
                        dispatchReceiver = call(TracerProvider_builder) {
                            dispatchReceiver = irGetObject(TracerProviderCompanion)
                        }
                        argument(0, irGetField(null, processor))
                    }
                }
            }
        }

        // val tracer = provider.tracerBuilder("").build()
        val tracer = buildField(
            name = "_tracer",
            type = Tracer.type(),
            static = true
        ) {
            initializer = expression {
                call(TracerBuilder_build) {
                    dispatchReceiver = call(TracerProvider_tracerBuilderFunction) {
                        dispatchReceiver = irGetField(null, provider)
                        argument(0, irString(""))
                    }
                }
            }
        }

        firstFile.addChildren(
            listOf(
                exporter,
                processor,
                provider,
                tracer
            )
        )
        // endregion

        // region functions
        val startSpan = buildFunction("_startSpan") {
            parameter {
                name = Name.identifier("name")
                type = string
            }
            parameter {
                name = Name.identifier("context")
                type = Context.type()
            }
            returnType = Span.type()

            body {
                val name = valueParameters[0]
                val context = valueParameters[1]

                // val spanBuilder = tracer.spanBuilder(name)
                val spanBuilder = irTemporary(
                    call(Tracer_spanBuilder) {
                        dispatchReceiver = irGetField(null, tracer)
                        argument(0, irGet(name))
                    }
                )

                // spanBuilder.setParent(context)
                +call(SpanBuilder_setParent) {
                    dispatchReceiver = irGet(spanBuilder)
                    argument(0, irGet(context))
                }

                // spanBuilder.setStartTimestamp(Clock.System.now())
                +call(SpanBuilder_setStartTimestamp) {
                    dispatchReceiver = irGet(spanBuilder)
                    argument(
                        0,
                        call(now) {
                            dispatchReceiver = irGetObject(System)
                        }
                    )
                }

                // val span = spanBuilder.startSpan()
                val span = irTemporary(
                    call(SpanBuilder_startSpan) {
                        dispatchReceiver = irGet(spanBuilder)
                    }
                )

                // context.with(span).makeCurrent()
                +call(Context_makeCurrent) {
                    dispatchReceiver = call(Context_with) {
                        dispatchReceiver = irGet(context)
                        argument(0, irGet(span))
                    }
                }

                // return span
                +irReturn(irGet(span))
            }
        }

        val endSpan = buildFunction("_endSpan") {
            parameter {
                name = Name.identifier("span")
                type = Span.type()
            }
            parameter {
                name = Name.identifier("context")
                type = Context.type()
            }
            returnType = unit

            body {
                // context.makeCurrent()
                +call(Context_makeCurrent) {
                    dispatchReceiver = irGet(valueParameters[1])
                }

                // span.end(Clock.System.now())
                +call(Span_end) {
                    dispatchReceiver = irGet(valueParameters[0])
                    argument(
                        0,
                        call(now) {
                            dispatchReceiver = irGetObject(System)
                        }
                    )
                }
            }
        }

        firstFile.addChildren(
            listOf(
                startSpan,
                endSpan,
            )
        )
        // endregion

        fun IrFunction.modify() {
            body {
                // val context = Context.current()
                val context = irTemporary(
                    call(Context_current) {
                        dispatchReceiver = irGetObject(ContextCompanion)
                    }
                )
                // val span = _startSpan(<function name>, context)
                val span = irTemporary(
                    call(startSpan) {
                        argument(0, irString(name.toString()))
                        argument(1, irGet(context))
                    }
                )

                val tryBlock: IrExpression = irBlock(
                    resultType = returnType
                ) {
                    for (statement in body!!.statements) +statement
                }

                +irTry(
                    tryBlock.type,
                    tryBlock,
                    listOf(),
                    irBlock {
                        // _endSpan(span, context)
                        +call(endSpan) {
                            argument(0, irGet(span))
                            argument(1, irGet(context))
                        }

                        if (name.toString() == "main") {
                            // processor.shutdown()
                            +call(Processor_shutdown) {
                                dispatchReceiver = irGetField(null, processor)
                            }

                            // await(exporter)
                            +call(await) {
                                argument(0, irGetField(null, exporter))
                            }
                        }
                    }
                )
            }
        }

        // region modify functions
        moduleFragment.files.forEach { file ->
            file.transform(
                object : IrElementTransformerVoidWithContext() {
                    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                        fun shouldModify(): Boolean {
                            val invalidOrigins = listOf<IrDeclarationOrigin>(
                                IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE,
                            )

                            return declaration !in functions &&                     // is not a generated function
                                declaration.body != null &&                         // has a body
                                declaration.name.toString() != "<init>" &&          // is not a constructor
                                declaration.name.toString() != "<anonymous>" &&     // is not an anonymous function
                                declaration.origin !in invalidOrigins
                        }

                        if (!shouldModify()) return declaration

                        declaration.modify()
                        return super.visitFunctionNew(declaration)
                    }
                },
                null
            )

            println("---${file.name}---")
            println(file.dump())
        }
        // endregion
    }
}
